/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.reactive

import monix.eval.Coeval.{Error, Now}
import monix.eval.{Callback, Coeval, Task}
import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.{Atomic, PaddingStrategy}
import monix.execution.cancelables.{AssignableCancelable, SingleAssignmentCancelable}
import monix.execution.{Ack, Scheduler}
import monix.reactive.Consumer.{ContraMapConsumer, MapAsyncConsumer, MapConsumer}
import monix.reactive.observers.{Subscriber, SyncSubscriber}
import scala.collection.immutable.Queue
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** The `Consumer` is a specification of how to consume an observable.
  *
  * It is a factory of subscribers with a completion callback attached,
  * being effectively a way to transform observables into
  * [[monix.eval.Task tasks]] for less error prone consuming of streams.
  */
trait Consumer[-In, +R] extends ((Observable[In]) => Task[R])
  with Serializable { self =>

  /** Builds a new [[monix.reactive.observers.Subscriber Subscriber]]
    * that can be subscribed to an [[Observable]] for consuming a stream,
    * with a callback that should eventually get called with a materialized
    * result.
    *
    * Notes:
    *
    *  - calling the callback must obey the contract for the
    *    [[monix.eval.Callback Callback]] type
    *  - the given callback should always get called, unless the
    *    upstream gets canceled
    *  - the given callback can be called when the subscriber is
    *    finished processing, but not necessarily
    *  - if the given callback isn't called after the subscriber is
    *    done processing, then the `Task` returned by [[apply]]
    *    loses the ability to cancel the stream, as that `Task` will
    *    complete before the stream is finished
    *
    * @param cb is the [[monix.eval.Callback Callback]] that will get
    *        called once the created subscriber is finished.
    * @param s is the [[monix.execution.Scheduler Scheduler]] that will
    *        get used for subscribing to the source observable and to
    *        process the events.
    *
    * @return a new subscriber that can be used to consume observables.
    */
  def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[In], AssignableCancelable)

  /** Given a source [[Observable]], convert it into a [[monix.eval.Task Task]]
    * by piggybacking on [[createSubscriber]].
    */
  def apply(source: Observable[In]): Task[R] =
    Task.create[R] { (scheduler, cb) =>
      val (out, c) = createSubscriber(cb, scheduler)
      // Start consuming the stream
      val subscription = source.subscribe(out)
      // Assign the observable subscription to our assignable,
      // thus the subscriber can cancel its subscription
      c := subscription
      // We cannot return the assignable returned by `createSubscriber`
      // because it might be a dummy
      subscription
    }

  /** Given a contravariant mapping function, transform
    * the source consumer by transforming the input.
    */
  def contramap[In2](f: In2 => In): Consumer[In2, R] =
    new ContraMapConsumer[In2,In,R](self,f)

  /** Given a mapping function, when consuming a stream,
    * applies the mapping function to the final result,
    * thus modifying the output of the source consumer.
    *
    * Note that for applying the mapping function an
    * asynchronous boundary is forced, otherwise it could
    * trigger a stack overflow exception. For more efficient
    * mapping of the result, it's probably better to `map`
    * the resulting `Task` on [[Observable.runWith]].
    *
    * @see [[mapAsync]] for a variant that can map the output
    *     to a `Task` that can be processed asynchronously.
    */
  def map[R2](f: R => R2): Consumer[In, R2] =
    new MapConsumer[In,R,R2](self, f)

  /** Given a mapping function, when consuming a stream,
    * applies the mapping function to the final result,
    * thus modifying the output of the source consumer.
    *
    * The mapping function returns a [[monix.eval.Task Task]]
    * that can be used to process results asynchronously.
    *
    * Note that for applying the mapping function an
    * asynchronous boundary is forced, otherwise it could
    * trigger a stack overflow exception. For more efficient
    * mapping of the result, it's probably better to `map`
    * the resulting `Task` on [[Observable.runWith]].
    */
  def mapAsync[R2](f: R => Task[R2]): Consumer[In, R2] =
    new MapAsyncConsumer[In,R,R2](self, f)
}

object Consumer {
  /** Given an [[Observer]] expression, builds a consumer from it. */
  def fromObserver[In](observer: Coeval[Observer[In]]): Consumer[In, Unit] =
    new FromObserverConsumer[In](observer)

  /** A consumer that immediately cancels its upstream after subscription. */
  def cancel[A]: Consumer.Sync[A, Unit] =
    CancelledConsumer

  /** A consumer that triggers an error immediately after subscribtion. */
  def raiseError[In, R](ex: Throwable): Consumer.Sync[In,R] =
    new RaiseErrorConsumer[In,R](ex)

  /** Given a fold function and an initial state value, applies the
    * fold function to every element of the stream and finally signaling
    * the accumulated value.
    *
    * @param initial is a lazy value that will be fed at first
    *        in the fold function as the initial state.
    * @param f is the function that calculates a new state on each
    *        emitted value by the stream, for accumulating state
    */
  def foldLeft[S,A](initial: Coeval[S])(f: (S,A) => S): Consumer.Sync[A,S] =
    new FoldLeftConsumer[A,S](initial, f)

  /** Given a fold function and an initial state value, applies the
    * fold function to every element of the stream and finally signaling
    * the accumulated value.
    *
    * The given fold function returns a `Task` that can execute an
    * asynchronous operation, with ordering of calls being guaranteed.
    *
    * @param initial is a lazy value that will be fed at first
    *        in the fold function as the initial state.
    * @param f is the function that calculates a new state on each
    *        emitted value by the stream, for accumulating state,
    *        returning a `Task` capable of asynchronous execution.
    */
  def foldLeftAsync[S,A](initial: Coeval[S])(f: (S,A) => Task[S]): Consumer[A,S] =
    new FoldLeftAsyncConsumer[A,S](initial, f)

  /** A consumer that will produce the first streamed value on
    * `onNext` after which the streaming gets cancelled.
    *
    * In case the stream is empty and so no `onNext` happen before
    * `onComplete`, then the a `NoSuchElementException` will get
    * triggered.
    */
  def head[A]: Consumer.Sync[A, A] =
    new HeadConsumer[A]

  /** A consumer that will produce a [[Notification]] of the first value
    * received (`onNext`, `onComplete` or `onError`), after which the
    * streaming gets cancelled.
    *
    *  - [[Notification.OnNext OnNext]] will be signaled on the first `onNext`
    *    event if it happens and the streaming will be stopped by `Stop`.
    *  - [[Notification.OnComplete OnComplete]] will be signaled if the stream
    *    was empty and thus completed without any `onNext`.
    *  - [[Notification.OnError OnError]] will be signaled if the stream
    *    was completed in error before the first `onNext` happened.
    */
  def firstNotification[A]: Consumer.Sync[A, Notification[A]] =
    new FirstNotificationConsumer[A]

  /** A simple consumer that consumes all elements of the
    * stream and then signals its completion.
    */
  def complete[A]: Consumer.Sync[A, Unit] =
    CompleteConsumer

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element and then finally signaling its completion.
    *
    * @param cb is the function that will be called for each element
    */
  def foreach[A](cb: A => Unit): Consumer.Sync[A, Unit] =
    new ForeachConsumer[A](cb)

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element and then finally signaling its completion.
    *
    * The given callback function returns a `Task` that can execute an
    * asynchronous operation, with ordering of calls being guaranteed.
    *
    * @param cb is the function that will be called for each element
    */
  def foreachAsync[A](cb: A => Task[Unit]): Consumer[A, Unit] =
    new ForeachAsyncConsumer[A](cb)

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element, in parallel, then finally signaling its
    * completion.
    *
    * @param parallelism is the maximum number of (logical) threads to use
    * @param cb is the function that will be called for each element
    */
  def foreachParallel[A](parallelism: Int)(cb: A => Unit): Consumer[A, Unit] =
    loadBalance(parallelism, foreach(cb))

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element, in parallel, then finally signaling its
    * completion.
    *
    * The given callback function returns a `Task` that can execute an
    * asynchronous operation, with ordering of calls being guaranteed
    * per subscriber.
    *
    * @param parallelism is the maximum number of (logical) threads to use
    * @param cb is the function that will be called for each element
    */
  def foreachParallelAsync[A](parallelism: Int)(cb: A => Task[Unit]): Consumer[A, Unit] =
    loadBalance(parallelism, foreachAsync(cb))

  /** Creates a consumer that when consuming the stream it will
    * start multiple subscribers and distribute the load between them,
    * with the following rules:
    *
    *  - items are pushed on free subscribers, respecting their contract,
    *    each item being pushed to the first available subscriber
    *  - in case no free subscribers are available, then the source
    *    gets back-pressured until free subscribers are available
    *  - in case of `onComplete` or `onError` only a single subscriber
    *    will receive the event, while the others will not
    *  - in case any of the subscribers returns `Stop` from `onNext`
    *    all streaming is stopped, including for the other subscribers
    *  - in case any of the subscribers triggers an error,
    *    all streaming is stopped, including for the other subscribers
    *  - so one subscriber canceling the streaming will cancel it for
    *    all the rest
    *
    * Also, the `Task` will complete when the streaming stops, either
    * because the upstream completes, or because one of the subscribers has
    * stopped the stream.
    */
  def loadBalance[A,R](parallelism: Int, consumer: Consumer[A,R]): Consumer[A, Unit] =
    new LoadBalanceConsumer[A,R](parallelism, consumer)

  /** Defines a synchronous [[Consumer]] that builds
    * [[monix.reactive.observers.SyncSubscriber synchronous subscribers]].
    */
  trait Sync[-In, +R] extends Consumer[In, R] {
    override def createSubscriber(cb: Callback[R], s: Scheduler): (SyncSubscriber[In], AssignableCancelable)
  }

  /** Implementation for [[Consumer.contramap]]. */
  private final class ContraMapConsumer[In2, -In, +R]
    (source: Consumer[In, R], f: In2 => In)
    extends Consumer[In2, R] {

    def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[In2], AssignableCancelable) = {
      val (out, c) = source.createSubscriber(cb, s)

      val out2 = new Subscriber[In2] {
        implicit val scheduler = out.scheduler
        // For protecting the contract
        private[this] var isDone = false

        def onError(ex: Throwable): Unit =
          if (!isDone) { isDone = true; out.onError(ex) }
        def onComplete(): Unit =
          if (!isDone) { isDone = true; out.onComplete() }

        def onNext(elem2: In2): Future[Ack] = {
          // Protects calls to user code from within the operator and
          // stream the error downstream if it happens, but if the
          // error happens because of calls to `onNext` or other
          // protocol calls, then the behavior should be undefined.
          var streamErrors = true
          try {
            val elem = f(elem2)
            streamErrors = false
            out.onNext(elem)
          } catch {
            case NonFatal(ex) if streamErrors =>
              onError(ex)
              Stop
          }
        }
      }

      (out2, c)
    }
  }

  /** Implementation for [[Consumer.map]]. */
  private final class MapConsumer[In, R, R2](source: Consumer[In,R], f: R => R2)
    extends Consumer[In, R2] {

    def createSubscriber(cb: Callback[R2], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      val cb1 = new Callback[R] {
        def onSuccess(value: R): Unit =
          s.execute(new Runnable {
            def run(): Unit = {
              var streamErrors = true
              try {
                val r2 = f(value)
                streamErrors = false
                cb.onSuccess(r2)
              } catch {
                case NonFatal(ex) =>
                  if (streamErrors) cb.onError(ex)
                  else s.reportFailure(ex)
              }
            }
          })

        def onError(ex: Throwable): Unit =
          s.execute(new Runnable {
            def run(): Unit = cb.onError(ex)
          })
      }

      source.createSubscriber(cb1, s)
    }
  }

  /** Implementation for [[Consumer.mapAsync]]. */
  private final class MapAsyncConsumer[In, R, R2](source: Consumer[In,R], f: R => Task[R2])
    extends Consumer[In, R2] {

    def createSubscriber(cb: Callback[R2], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      val cb1 = new Callback[R] {
        def onSuccess(value: R): Unit =
          s.execute(new Runnable {
            def run(): Unit = {
              implicit val scheduler = s
              // For protecting the contract, as if a call was already made to
              // `onSuccess`, then we can't call `onError`
              var streamErrors = true
              try {
                val task = f(value)
                // result might be available immediately
                task.coeval.value match {
                  case Right(immediate) =>
                    streamErrors = false
                    cb.onSuccess(immediate)
                  case Left(async) =>
                    streamErrors = false
                    async.onComplete(cb)
                }
              } catch {
                case NonFatal(ex) =>
                  if (streamErrors) cb.onError(ex)
                  else s.reportFailure(ex)
              }
            }
          })

        def onError(ex: Throwable): Unit =
          s.execute(new Runnable { def run(): Unit = cb.onError(ex) })
      }

      source.createSubscriber(cb1, s)
    }
  }

  /** Implementation for [[cancel]]. */
  private object CancelledConsumer extends Consumer.Sync[Any, Unit] {
    def createSubscriber(cb: Callback[Unit], s: Scheduler): (SyncSubscriber[Any], AssignableCancelable) = {
      val out = new SyncSubscriber[Any] {
        implicit val scheduler = s
        def onNext(elem: Any): Ack = Stop
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)
      }

      cb.onSuccess(())
      (out, AssignableCancelable.alreadyCanceled)
    }
  }

  /** Implementation for [[head]] */
  private class HeadConsumer[A] extends Consumer.Sync[A, A] {
    override def createSubscriber(cb: Callback[A], s: Scheduler): (SyncSubscriber[A], AssignableCancelable) = {
      val out = new SyncSubscriber[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Ack = {
          isDone = true
          cb.onSuccess(elem)
          Stop
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(new NoSuchElementException("head"))
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[firstNotification]] */
  private class FirstNotificationConsumer[A] extends Consumer.Sync[A, Notification[A]] {
    override def createSubscriber(cb: Callback[Notification[A]], s: Scheduler): (SyncSubscriber[A], AssignableCancelable) = {
      val out = new SyncSubscriber[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Ack = {
          isDone = true
          cb.onSuccess(Notification.OnNext(elem))
          Stop
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(Notification.OnComplete)
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(Notification.OnError(ex))
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[foldLeft]]. */
  private final class FoldLeftConsumer[A,R](initial: Coeval[R], f: (R,A) => R)
    extends Consumer.Sync[A,R] {

    def createSubscriber(cb: Callback[R], s: Scheduler): (SyncSubscriber[A], AssignableCancelable) = {
      val out = new SyncSubscriber[A] {
        implicit val scheduler = s
        private[this] var isDone = false
        private[this] var state = initial.value

        def onNext(elem: A): Ack = {
          // Protects calls to user code from within the operator,
          // as a matter of contract.
          try {
            state = f(state, elem)
            Continue
          } catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(state)
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[foldLeftAsync]]. */
  private final class FoldLeftAsyncConsumer[A,R](initial: Coeval[R], f: (R,A) => Task[R])
    extends Consumer[A,R] {

    def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[A], AssignableCancelable) = {
      val out = new Subscriber[A] {
        implicit val scheduler = s
        private[this] var isDone = false
        private[this] var state = initial.value
        private[this] var ack: Future[Ack] = Continue

        def onNext(elem: A): Future[Ack] = {
          // Protects calls to user code from within the operator,
          // as a matter of contract.
          ack = try {
            f(state, elem).coeval.value match {
              case Left(future) =>
                future.map { update =>
                  state = update
                  Continue
                }
              case Right(update) =>
                state = update
                Continue
            }
          } catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }

          // Need to store ack on each onNext, because we need
          // to back-pressure onComplete and onError
          ack
        }

        def onComplete(): Unit =
          ack.syncOnContinue {
            if (!isDone) {
              isDone = true
              cb.onSuccess(state)
            }
          }

        def onError(ex: Throwable): Unit =
          ack.syncOnContinue {
            if (!isDone) {
              isDone = true
              cb.onError(ex)
            }
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[complete]] */
  private object CompleteConsumer extends Consumer.Sync[Any, Unit] {
    override def createSubscriber(cb: Callback[Unit], s: Scheduler): (SyncSubscriber[Any], AssignableCancelable) = {
      val out = new SyncSubscriber[Any] {
        implicit val scheduler = s
        def onNext(elem: Any): Ack = Continue
        def onComplete(): Unit = cb.onSuccess(())
        def onError(ex: Throwable): Unit = cb.onError(ex)
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[foreach]]. */
  private final class ForeachConsumer[A](f: A => Unit)
    extends Consumer.Sync[A, Unit] {

    def createSubscriber(cb: Callback[Unit], s: Scheduler): (SyncSubscriber[A], AssignableCancelable) = {
      val out = new SyncSubscriber[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Ack = {
          try {
            f(elem)
            Continue
          } catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(())
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[foreachAsync]]. */
  private final class ForeachAsyncConsumer[A](f: A => Task[Unit])
    extends Consumer[A, Unit] {

    def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber[A], AssignableCancelable) = {
      val out = new Subscriber[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Future[Ack] = {
          try {
            f(elem).coeval.value match {
              case Left(future) =>
                future.map(_ => Continue)
              case Right(()) =>
                Continue
            }
          } catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(())
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[Consumer.raiseError]]. */
  private final class RaiseErrorConsumer[-In,R](ex: Throwable)
    extends Consumer.Sync[In,R] {

    def createSubscriber(cb: Callback[R], s: Scheduler): (SyncSubscriber[In], AssignableCancelable) = {
      val out = new SyncSubscriber[Any] {
        implicit val scheduler = s
        def onNext(elem: Any): Ack = Stop
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)
      }

      cb.onError(ex)
      (out, AssignableCancelable.alreadyCanceled)
    }
  }

  /** Implementation for [[Consumer.fromObserver]]. */
  private final class FromObserverConsumer[In](obs: Coeval[Observer[In]])
    extends Consumer[In, Unit] {

    def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      obs.runAttempt match {
        case Error(ex) =>
          Consumer.raiseError(ex).createSubscriber(cb,s)

        case Now(out) =>
          val sub = new Subscriber[In] { self =>
            implicit val scheduler = s

            private[this] val isDone = Atomic(false)
            private def signal(ex: Throwable): Unit =
              if (!isDone.getAndSet(true)) {
                if (ex == null) cb.onSuccess(())
                else cb.onError(ex)
              }

            def onNext(elem: In): Future[Ack] = {
              val ack = try out.onNext(elem) catch {
                case NonFatal(ex) => Future.failed(ex)
              }

              ack.syncOnComplete {
                case Success(result) =>
                  if (result == Stop) signal(null)
                case Failure(ex) =>
                  signal(ex)
              }

              ack
            }

            def onComplete(): Unit = signal(null)
            def onError(ex: Throwable): Unit = signal(ex)
          }

          (sub, AssignableCancelable.dummy)
      }
    }
  }

  /** Implementation for [[monix.reactive.Consumer.loadBalance]]. */
  private[reactive] final class LoadBalanceConsumer[-In, U]
    (parallelism: Int, consumer: Consumer[In, U])
    extends Consumer[In, Unit] {

    require(parallelism > 1, s"parallelism = $parallelism, should be > 1")

    // NOTE: onFinish MUST BE synchronized by `self` and
    // double-checked by means of `isDone`
    def createSubscriber(onFinish: Callback[Unit], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      // Assignable cancelable returned, can be used to cancel everything
      // since it will be assigned the stream subscription
      val mainCancelable = SingleAssignmentCancelable()

      val balanced = new Subscriber[In] { self =>
        implicit val scheduler = s
        // Trying to prevent contract violations, once this turns
        // true, then no final events are allowed to happen.
        // MUST BE synchronized by `self`.
        private[this] var isDone = false

        // Asynchronous queue that serves idle subscribers waiting
        // for something to process, or that puts the stream on wait
        // until there are subscribers available
        private[this] val subscribersQueue = {
          var initial = Queue.empty[Subscriber[In]]
          // When the callback gets called by each subscriber, on success we
          // do nothing because for normal completion we are listing on
          // `Stop` events from onNext, but on failure we deactivate all.
          val callback = new Callback[U] {
            def onSuccess(value: U) = ()
            def onError(ex: Throwable) = interruptAll(ex)
          }

          var i = 0
          while (i < parallelism) {
            val (out, c) = consumer.createSubscriber(callback, s)
            // Every created subscriber has the opportunity to cancel the
            // main subscription if needed, cancellation thus happening globally
            c := mainCancelable
            initial = initial.enqueue(out)
            i += 1
          }

          new LoadBalanceConsumer.AsyncQueue(initial, parallelism)
        }

        def onNext(elem: In): Future[Ack] = {
          // Declares a stop event, completing the callback
          def stop(): Stop = self.synchronized {
            // Protecting against contract violations
            if (!isDone) {
              isDone = true
              // Sending completion event, since we are done
              onFinish.onSuccess(())
            }

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

        /** When Stop or error is received, this makes sure the
          * streaming gets interrupted!
          */
        private def interruptAll(ex: Throwable): Unit = synchronized {
          if (!isDone) {
            isDone = true
            mainCancelable.cancel()
            subscribersQueue.deactivateAll()
            if (ex == null) onFinish.onSuccess(())
            else onFinish.onError(ex)
          } else if (ex != null) {
            scheduler.reportFailure(ex)
          }
        }

        /** Given a subscriber, signals the given element, then return
          * the subscriber to the queue if possible.
          */
        private def signalNext(out: Subscriber[In], elem: In): Unit = {
          // We are forcing an asynchronous boundary here, since we
          // don't want to block the main thread!
          scheduler.execute(new Runnable {
            def run(): Unit = {
              try out.onNext(elem).syncOnComplete {
                case Success(ack) =>
                  ack match {
                    case Continue =>
                      // We have permission to continue from this subscriber
                      // so returning it to the queue, to be reused
                      subscribersQueue.offer(out)
                    case Stop =>
                      // Deactivating the subscriber. In case all subscribers
                      // have been deactivated, then we are done
                      if (subscribersQueue.deactivate())
                        interruptAll(null)
                  }
                case Failure(ex) =>
                  interruptAll(ex)
              } catch {
                case NonFatal(ex) =>
                  interruptAll(ex)
              }
            }
          })
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
              case null => Future.successful(())
              case out =>
                try {
                  if (ex == null) out.onComplete()
                  else out.onError(ex)
                } catch {
                  case NonFatal(err) => s.reportFailure(err)
                }

                if (activeCount > 0) loop(activeCount-1)
                else Future.successful(())
            }
          }

          self.synchronized {
            // Protecting against contract violations.
            if (!isDone) {
              isDone = true
              // Starting the loop
              loop(subscribersQueue.activeCount).onComplete {
                case Success(()) =>
                  if (ex != null) onFinish.onError(ex)
                  else onFinish.onSuccess(())
                case Failure(err) =>
                  onFinish.onError(err)
              }
            } else if (ex != null) {
              scheduler.reportFailure(ex)
            }
          }
        }
      }

      (balanced, mainCancelable)
    }
  }

  private[reactive] object LoadBalanceConsumer {
    private final class AsyncQueue[In](
      initialQueue: Queue[Subscriber[In]], parallelism: Int) {

      private[this] val stateRef = {
        val initial = Available(initialQueue, parallelism) : State[In]
        Atomic.withPadding(initial, PaddingStrategy.LeftRight256)
      }

      def activeCount: Int =
        stateRef.get.activeCount

      def offer(value: Subscriber[In]): Unit =
        stateRef.get match {
          case current @ Available(queue, ac) =>
            if (ac <= 0)
              throw new IllegalStateException("offer after activeCount is zero")

            val update = Available(queue.enqueue(value), ac)
            if (!stateRef.compareAndSet(current, update))
              offer(value)

          case current @ Waiting(promise, ac) =>
            val update = Available[In](Queue.empty, ac)
            if (!stateRef.compareAndSet(current, update))
              offer(value)
            else
              promise.success(value)
        }

      def poll(): Future[Subscriber[In]] =
        stateRef.get match {
          case current @ Available(queue, ac) =>
            if (ac <= 0)
              Future.successful(null)
            else if (queue.isEmpty) {
              val p = Promise[Subscriber[In]]()
              val update = Waiting(p, ac)
              if (!stateRef.compareAndSet(current, update))
                poll()
              else
                p.future
            } else {
              val (s, newQueue) = queue.dequeue
              if (!stateRef.compareAndSet(current, Available(newQueue, ac)))
                poll()
              else
                Future.successful(s)
            }
          case Waiting(_,_) =>
            Future.failed(new IllegalStateException("waiting in poll()"))
        }

      def deactivateAll(): Unit =
        stateRef.set(Available(Queue.empty, 0))

      def deactivate(): Boolean =
        stateRef.get match {
          case current @ Available(queue, count) =>
            if (count <= 0) true else {
              val update = Available(queue, count-1)
              if (!stateRef.compareAndSet(current, update))
                deactivate()
              else
                count == 1
            }

          case current @ Waiting(promise, count) =>
            val update =
              if (count - 1 > 0) Waiting(promise, count-1)
              else Available[In](Queue.empty, 0)

            if (!stateRef.compareAndSet(current, update))
              deactivate()
            else if (count == 1) {
              promise.success(null)
              true
            }
            else
              false
        }
    }

    private sealed trait State[In] {
      def activeCount: Int
    }

    private final case class Available[In](
      available: Queue[Subscriber[In]],
      activeCount: Int)
      extends State[In]

    private final case class Waiting[In](
      promise: Promise[Subscriber[In]],
      activeCount: Int)
      extends State[In]
  }
}
