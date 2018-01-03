/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.reactive.internal.consumers

import monix.eval.Callback
import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.execution.atomic.{Atomic, PaddingStrategy}
import monix.execution.cancelables.{AssignableCancelable, SingleAssignmentCancelable}
import monix.execution.misc.NonFatal
import monix.reactive.Consumer
import monix.reactive.internal.consumers.LoadBalanceConsumer.IndexedSubscriber
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.collection.immutable.{BitSet, Queue}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}


/** Implementation for [[monix.reactive.Consumer.loadBalance]]. */
private[reactive]
final class LoadBalanceConsumer[-In, R]
  (parallelism: Int, consumers: Array[Consumer[In, R]])
  extends Consumer[In, List[R]] {

  require(parallelism > 0, s"parallelism = $parallelism, should be > 0")
  require(consumers.length > 0, "consumers list must not be empty")

  // NOTE: onFinish MUST BE synchronized by `self` and
  // double-checked by means of `isDone`
  def createSubscriber(onFinish: Callback[List[R]], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
    // Assignable cancelable returned, can be used to cancel everything
    // since it will be assigned the stream subscription
    val mainCancelable = SingleAssignmentCancelable()

    val balanced = new Subscriber[In] { self =>
      implicit val scheduler = s

      // Trying to prevent contract violations, once this turns
      // true, then no final events are allowed to happen.
      // MUST BE synchronized by `self`.
      private[this] var isUpstreamComplete = false

      // Trying to prevent contract violations. Turns true in case
      // we already signaled a result upstream.
      // MUST BE synchronized by `self`.
      private[this] var isDownstreamDone = false

      // Stores the error that was reported upstream - basically
      // multiple subscribers can report multiple errors, but we
      // emit the first one, so in case multiple errors happen we
      // want to log them, but only if they aren't the same reference
      // MUST BE synchronized by `self`
      private[this] var reportedError: Throwable = _

      // Results accumulator - when length == parallelism,
      // that's when we need to trigger `onFinish.onSuccess`.
      // MUST BE synchronized by `self`
      private[this] val accumulator = ListBuffer.empty[R]

      /** Builds cancelables for subscribers. */
      private def newCancelableFor(out: IndexedSubscriber[In]): Cancelable =
        new Cancelable {
          private[this] var isCanceled = false
          // Forcing an asynchronous boundary, to avoid any possible
          // initialization issues (in building subscribersQueue) or
          // stack overflows and other problems
          def cancel(): Unit = scheduler.executeAsync { () =>
            // We are required to synchronize, because we need to
            // make sure that subscribersQueue is fully created before
            // triggering any cancellation!
            self.synchronized {
              // Guards the idempotency contract of cancel(); not really
              // required, because `deactivate()` should be idempotent, but
              // since we are doing an expensive synchronize, we might as well
              if (!isCanceled) {
                isCanceled = true
                interruptOne(out, null)
              }
            }
          }
        }

      // Asynchronous queue that serves idle subscribers waiting
      // for something to process, or that puts the stream on wait
      // until there are subscribers available
      private[this] val subscribersQueue = self.synchronized {
        var initial = Queue.empty[IndexedSubscriber[In]]
        // When the callback gets called by each subscriber, on success we
        // do nothing because for normal completion we are listing on
        // `Stop` events from onNext, but on failure we deactivate all.
        val callback = new Callback[R] {
          def onSuccess(value: R): Unit =
            accumulate(value)
          def onError(ex: Throwable): Unit =
            interruptAll(ex)
        }

        val arrLen = consumers.length
        var i = 0

        while (i < parallelism) {
          val (out, c) = consumers(i % arrLen).createSubscriber(callback, s)
          val indexed = IndexedSubscriber(i, out)
          // Every created subscriber has the opportunity to cancel the
          // main subscription if needed, cancellation thus happening globally
          c := newCancelableFor(indexed)
          initial = initial.enqueue(indexed)
          i += 1
        }

        new LoadBalanceConsumer.AsyncQueue(initial, parallelism)
      }

      def onNext(elem: In): Future[Ack] = {
        // Declares a stop event, completing the callback
        def stop(): Ack = self.synchronized {
          // Protecting against contract violations
          isUpstreamComplete = true
          Stop
        }

        // Are there subscribers available?
        val sf = subscribersQueue.poll()
        // Doing a little optimization to prevent one async boundary
        sf.value match {
          case Some(Success(subscriber)) =>
            // As a matter of protocol, if null values happen, then
            // this means that all subscribers have been deactivated and so
            // we should cancel the streaming.
            if (subscriber == null) stop() else {
              signalNext(subscriber, elem)
              Continue
            }
          case _ => sf.map {
            case null => stop()
            case subscriber =>
              signalNext(subscriber, elem)
              Continue
          }
        }
      }

      /** Triggered whenever the subscribers are finishing with onSuccess */
      private def accumulate(value: R): Unit = self.synchronized {
        if (!isDownstreamDone) {
          accumulator += value
          if (accumulator.length == parallelism) {
            isDownstreamDone = true
            onFinish.onSuccess(accumulator.toList)
            // GC relief
            accumulator.clear()
          }
        }
      }

      /** Triggered whenever we need to signal an `onError` upstream */
      private def reportErrorUpstream(ex: Throwable) = self.synchronized {
        if (isDownstreamDone) {
          // We only report errors that we haven't
          // reported to upstream by means of `onError`!
          if (reportedError != ex)
            scheduler.reportFailure(ex)
        } else {
          isDownstreamDone = true
          reportedError = ex
          onFinish.onError(ex)
          // GC relief
          accumulator.clear()
        }
      }

      /** Called whenever a subscriber stops its subscription, or
        * when an error gets thrown.
        */
      private def interruptOne(out: IndexedSubscriber[In], ex: Throwable): Unit = {
        // Deactivating the subscriber. In case all subscribers
        // have been deactivated, then we are done
        if (subscribersQueue.deactivate(out))
          interruptAll(ex)
      }

      /** When Stop or error is received, this makes sure the
        * streaming gets interrupted!
        */
      private def interruptAll(ex: Throwable): Unit = self.synchronized {
        // All the following operations are idempotent!
        isUpstreamComplete = true
        mainCancelable.cancel()
        subscribersQueue.deactivateAll()
        // Is this an error to signal?
        if (ex != null) reportErrorUpstream(ex)
      }

      /** Given a subscriber, signals the given element, then return
        * the subscriber to the queue if possible.
        */
      private def signalNext(out: IndexedSubscriber[In], elem: In): Unit = {
        // We are forcing an asynchronous boundary here, since we
        // don't want to block the main thread!
        scheduler.executeAsync { () =>
          try out.out.onNext(elem).syncOnComplete {
            case Success(ack) =>
              ack match {
                case Continue =>
                  // We have permission to continue from this subscriber
                  // so returning it to the queue, to be reused
                  subscribersQueue.offer(out)
                case Stop =>
                  interruptOne(out, null)
              }
            case Failure(ex) =>
              interruptAll(ex)
          } catch {
            case NonFatal(ex) =>
              interruptAll(ex)
          }
        }
      }

      def onComplete(): Unit =
        signalComplete(null)
      def onError(ex: Throwable): Unit =
        signalComplete(ex)

      private def signalComplete(ex: Throwable): Unit = {
        def loop(activeCount: Int): Future[Unit] = {
          // If we no longer have active subscribers to
          // push events into, then the loop is finished
          if (activeCount <= 0)
            Future.successful(())
          else subscribersQueue.poll().flatMap {
            // By protocol, if a null happens, then there are
            // no more active subscribers available
            case null =>
              Future.successful(())
            case subscriber =>
              try {
                if (ex == null) subscriber.out.onComplete()
                else subscriber.out.onError(ex)
              } catch {
                case NonFatal(err) => s.reportFailure(err)
              }

              if (activeCount > 0) loop(activeCount-1)
              else Future.successful(())
          }
        }

        self.synchronized {
          // Protecting against contract violations.
          if (!isUpstreamComplete) {
            isUpstreamComplete = true

            // Starting the loop
            loop(subscribersQueue.activeCount).onComplete {
              case Success(()) =>
                if (ex != null) reportErrorUpstream(ex)
              case Failure(err) =>
                reportErrorUpstream(err)
            }
          } else if (ex != null) {
            reportErrorUpstream(ex)
          }
        }
      }
    }

    (balanced, mainCancelable)
  }
}

private[reactive] object LoadBalanceConsumer {
  /** Wraps a subscriber implementation into one
    * that exposes an ID.
    */
  private[reactive] final
  case class IndexedSubscriber[-In](id: Int, out: Subscriber[In])

  private final class AsyncQueue[In](
    initialQueue: Queue[IndexedSubscriber[In]], parallelism: Int) {

    private[this] val stateRef = {
      val initial: State[In] = Available(initialQueue, BitSet.empty, parallelism)
      Atomic.withPadding(initial, PaddingStrategy.LeftRight256)
    }

    def activeCount: Int =
      stateRef.get.activeCount

    @tailrec
    def offer(value: IndexedSubscriber[In]): Unit =
      stateRef.get match {
        case current @ Available(queue, canceledIDs, ac) =>
          if (ac > 0 && !canceledIDs(value.id)) {
            val update = Available(queue.enqueue(value), canceledIDs, ac)
            if (!stateRef.compareAndSet(current, update))
              offer(value)
          }

        case current @ Waiting(promise, canceledIDs, ac) =>
          if (!canceledIDs(value.id)) {
            val update = Available[In](Queue.empty, canceledIDs, ac)
            if (!stateRef.compareAndSet(current, update))
              offer(value)
            else
              promise.success(value)
          }
      }

    @tailrec
    def poll(): Future[IndexedSubscriber[In]] =
      stateRef.get match {
        case current @ Available(queue, canceledIDs, ac) =>
          if (ac <= 0)
            Future.successful(null)
          else if (queue.isEmpty) {
            val p = Promise[IndexedSubscriber[In]]()
            val update = Waiting(p, canceledIDs, ac)
            if (!stateRef.compareAndSet(current, update))
              poll()
            else
              p.future
          }
          else {
            val (ref, newQueue) = queue.dequeue
            val update = Available(newQueue, canceledIDs, ac)
            if (!stateRef.compareAndSet(current, update))
              poll()
            else
              Future.successful(ref)
          }
        case Waiting(_,_,_) =>
          Future.failed(new IllegalStateException("waiting in poll()"))
      }

    @tailrec
    def deactivateAll(): Unit =
      stateRef.get match {
        case current @ Available(_, canceledIDs, _) =>
          val update: State[In] = Available(Queue.empty, canceledIDs, 0)
          if (!stateRef.compareAndSet(current, update))
            deactivateAll()
        case current @ Waiting(promise, canceledIDs, _) =>
          val update: State[In] = Available(Queue.empty, canceledIDs, 0)
          if (!stateRef.compareAndSet(current, update))
            deactivateAll()
          else
            promise.success(null)
      }

    @tailrec
    def deactivate(ref: IndexedSubscriber[In]): Boolean =
      stateRef.get match {
        case current @ Available(queue, canceledIDs, count) =>
          if (count <= 0) true else {
            val update = if (canceledIDs(ref.id)) current else {
              val newQueue = queue.filterNot(_.id == ref.id)
              Available(newQueue, canceledIDs+ref.id, count-1)
            }

            if (update.activeCount == current.activeCount)
              false // nothing to update
            else if (!stateRef.compareAndSet(current, update))
              deactivate(ref) // retry
            else
              update.activeCount == 0
          }

        case current @ Waiting(promise, canceledIDs, count) =>
          if (canceledIDs(ref.id)) count <= 0 else {
            val update =
              if (count - 1 > 0) Waiting(promise, canceledIDs+ref.id, count-1)
              else Available[In](Queue.empty, canceledIDs+ref.id, 0)

            if (!stateRef.compareAndSet(current, update))
              deactivate(ref) // retry
            else if (update.activeCount <= 0) {
              promise.success(null)
              true
            }
            else
              false
          }
      }
  }

  private[reactive] sealed trait State[In] {
    def activeCount: Int
    def canceledIDs: Set[Int]
  }

  private[reactive] final case class Available[In](
    available: Queue[IndexedSubscriber[In]],
    canceledIDs: BitSet,
    activeCount: Int)
    extends State[In]

  private[reactive] final case class Waiting[In](
    promise: Promise[IndexedSubscriber[In]],
    canceledIDs: BitSet,
    activeCount: Int)
    extends State[In]
}
