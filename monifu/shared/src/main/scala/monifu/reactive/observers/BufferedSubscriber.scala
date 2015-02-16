/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.reactive.observers

import monifu.collection.mutable.ConcurrentQueue
import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.padded.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.BufferPolicy.{BackPressured, OverflowTriggering, Unbounded}
import monifu.reactive._

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.Failure
import scala.util.control.NonFatal

/**
 * Interface describing [[monifu.reactive.Observer Observer]] wrappers
 * that are thread-safe (can receive concurrent events) and that
 * return an immediate `Continue` when receiving `onNext`
 * events. Meant to be used by data sources that cannot uphold the
 * no-concurrent events and the back-pressure related requirements
 * (i.e. data-sources that cannot wait on `Future[Continue]` for
 * sending the next event).
 *
 * Implementations of this interface have the following contract:
 *
 *  - `onNext` / `onError` / `onComplete` of this interface MAY be
 *    called concurrently
 *  - `onNext` SHOULD return an immediate `Continue`, as long as the
 *    buffer is not full and the underlying observer hasn't signaled
 *    `Cancel` (N.B. due to the asynchronous nature, `Cancel` signaled
 *    by the underlying observer may be noticed later, so
 *    implementations of this interface make no guarantee about queued
 *    events - which could be generated, queued and dropped on the
 *    floor later)
 *  - `onNext` MUST return an immediate `Cancel` result, after it
 *    notices that the underlying observer signaled `Cancel` (due to
 *    the asynchronous nature of observers, this may happen later and
 *    queued events might get dropped on the floor)
 *  - in general the contract for the underlying Observer is fully
 *    respected (grammar, non-concurrent notifications, etc...)
 *  - when the underlying observer canceled (by returning `Cancel`),
 *    or when a concurrent upstream data source triggered an error,
 *    this SHOULD eventually be noticed and acted upon
 *  - as long as the buffer isn't full and the underlying observer
 *    isn't `Cancel`, then implementations of this interface SHOULD
 *    not lose events in the process
 *  - the buffer MAY BE either unbounded or bounded, in case of
 *    bounded buffers, then an appropriate policy needs to be set for
 *    when the buffer overflows - either an `onError` triggered in the
 *    underlying observer coupled with a `Cancel` signaled to the
 *    upstream data sources, or dropping events from the head or the
 *    tail of the queue, or attempting to apply back-pressure, etc...
 *
 * See [[monifu.reactive.BufferPolicy BufferPolicy]] for the buffer
 * policies available.
 */
trait BufferedSubscriber[-T] extends Subscriber[T]


object BufferedSubscriber {
  def apply[T](observer: Observer[T], bufferPolicy: BufferPolicy = Unbounded)
      (implicit s: Scheduler): BufferedSubscriber[T] = {

    bufferPolicy match {
      case Unbounded =>
        SynchronousBufferedSubscriber.unbounded(observer)
      case OverflowTriggering(bufferSize) =>
        SynchronousBufferedSubscriber.overflowTriggering(observer, bufferSize)
      case BackPressured(bufferSize) =>
        BackPressuredBufferedSubscriber(observer, bufferSize)
      case _ =>
        throw new NotImplementedError(s"BufferedSubscriber($bufferPolicy)")
    }
  }
}

/**
 * A highly optimized [[BufferedSubscriber]] implementation. It supports 2
 * [[monifu.reactive.BufferPolicy buffer policies]] - unbounded or bounded and terminated
 * with a [[monifu.reactive.BufferOverflowException BufferOverflowException]].
 *
 * To create an instance using an unbounded policy: {{{
 *   // by default, the constructor for BufferedSubscriber is returning this unbounded variant
 *   BufferedSubscriber(observer)
 *   
 *   // or you can specify the Unbounded policy explicitly
 *   import monifu.reactive.BufferPolicy.Unbounded
 *   val buffered = BufferedSubscriber(observer, bufferPolicy = Unbounded)
 * }}}
 *
 * To create a bounded buffered observable that triggers
 * [[monifu.reactive.BufferOverflowException BufferOverflowException]]
 * when over capacity: {{{
 *   import monifu.reactive.BufferPolicy.OverflowTriggering
 *   // triggers buffer overflow error after 10000 messages
 *   val buffered = BufferedSubscriber(observer, bufferPolicy = OverflowTriggering(bufferSize = 10000))
 * }}}
 *
 * @param underlying is the underlying observer receiving the queued events
 * @param bufferSize is the maximum buffer size, or zero if unbounded
 */
final class SynchronousBufferedSubscriber[-T] private
    (underlying: Observer[T], bufferSize: Int = 0)(implicit val scheduler: Scheduler)
  extends BufferedSubscriber[T] with SynchronousSubscriber[T] {

  require(bufferSize >= 0, "bufferSize must be a positive number")

  private[this] val queue = ConcurrentQueue.empty[T]
  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  @volatile private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  @volatile private[this] var downstreamIsDone = false
  // for enforcing non-concurrent updates
  private[this] val itemsToPush = Atomic(0)

  val observer: SynchronousObserver[T] = new SynchronousObserver[T] {
    def onNext(elem: T): Ack = {
      if (!upstreamIsComplete && !downstreamIsDone) {
        try {
          queue.offer(elem)
          pushToConsumer()
          Continue
        }
        catch {
          case NonFatal(ex) =>
            onError(ex)
            Cancel
        }
      }
      else
        Cancel
    }

    def onError(ex: Throwable) = {
      if (!upstreamIsComplete && !downstreamIsDone) {
        errorThrown = ex
        upstreamIsComplete = true
        pushToConsumer()
      }
    }

    def onComplete() = {
      if (!upstreamIsComplete && !downstreamIsDone) {
        upstreamIsComplete = true
        pushToConsumer()
      }
    }
  }


  @tailrec
  private[this] def pushToConsumer(): Unit = {
    val currentNr = itemsToPush.get

    if (bufferSize == 0) {
      // unbounded branch
      if (!itemsToPush.compareAndSet(currentNr, currentNr + 1))
        pushToConsumer()
      else if (currentNr == 0)
        scheduler.execute(new Runnable {
          def run() = fastLoop(0)
        })
    }
    else {
      // triggering overflow branch
      if (currentNr >= bufferSize && !upstreamIsComplete) {
        observer.onError(new BufferOverflowException(
          s"Downstream observer is too slow, buffer over capacity with a specified buffer size of $bufferSize and" +
            s" $currentNr events being left for push"))
      }
      else if (!itemsToPush.compareAndSet(currentNr, currentNr + 1))
        pushToConsumer()
      else if (currentNr == 0)
        scheduler.execute(new Runnable {
          def run() = fastLoop(0)
        })
    }
  }

  private[this] def rescheduled(processed: Int): Unit = {
    fastLoop(processed)
  }

  @tailrec
  private[this] def fastLoop(processed: Int): Unit = {
    if (!downstreamIsDone) {
      // errors have priority
      val hasError = errorThrown ne null
      val next = queue.poll()

      if (next != null && !hasError) {
        underlying.onNext(next) match {
          case sync if sync.isCompleted =>
            sync match {
              case continue if continue == Continue || continue.value.get == Continue.IsSuccess =>
                // process next
                fastLoop(processed + 1)

              case done if done == Cancel || done.value.get == Cancel.IsSuccess =>
                // ending loop
                downstreamIsDone = true
                itemsToPush.set(0)

              case error if error.value.get.isFailure =>
                // ending loop
                downstreamIsDone = true
                itemsToPush.set(0)
                underlying.onError(error.value.get.failed.get)
            }

          case async =>
            async.onComplete {
              case Continue.IsSuccess =>
                // re-run loop (in different thread)
                rescheduled(processed + 1)

              case Cancel.IsSuccess =>
                // ending loop
                downstreamIsDone = true
                itemsToPush.set(0)

              case Failure(ex) =>
                // ending loop
                downstreamIsDone = true
                itemsToPush.set(0)
                underlying.onError(ex)

              case other =>
                // never happens, but to appease Scala's compiler
                downstreamIsDone = true
                itemsToPush.set(0)
                underlying.onError(new MatchError(s"$other"))
            }
        }
      }
      else if (upstreamIsComplete || hasError) {
        // Race-condition check, but if upstreamIsComplete=true is visible, then the queue should be fully published
        // because there's a clear happens-before relationship between queue.offer() and upstreamIsComplete=true
        // NOTE: errors have priority, so in case of an error seen, then the loop is stopped
        if (!hasError && !queue.isEmpty) {
          fastLoop(processed)
        }
        else {
          // ending loop
          downstreamIsDone = true
          itemsToPush.set(0)
          queue.clear() // for GC purposes
          if (errorThrown ne null)
            underlying.onError(errorThrown)
          else
            underlying.onComplete()
        }
      }
      else {
        val remaining = itemsToPush.decrementAndGet(processed)
        // if the queue is non-empty (i.e. concurrent modifications just happened)
        // then start all over again
        if (remaining > 0) fastLoop(0)
      }
    }
  }
}

object SynchronousBufferedSubscriber {
  def unbounded[T](observer: Observer[T])(implicit s: Scheduler): SynchronousBufferedSubscriber[T] =
    new SynchronousBufferedSubscriber[T](observer)
  
  def overflowTriggering[T](observer: Observer[T], bufferSize: Int)(implicit s: Scheduler): SynchronousBufferedSubscriber[T] =
    new SynchronousBufferedSubscriber[T](observer, bufferSize)
}

final class BackPressuredBufferedSubscriber[-T] private
    (underlying: Observer[T], bufferSize: Int)(implicit val scheduler: Scheduler)
  extends BufferedSubscriber[T] { self =>

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  private[this] val queue = ConcurrentQueue.empty[T]
  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  @volatile private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  @volatile private[this] var downstreamIsDone = false

  // for enforcing non-concurrent updates and back-pressure
  // all access must be synchronized
  private[this] val lock = new AnyRef
  private[this] var itemsToPush = 0
  private[this] var nextAckPromise = Promise[Ack]()
  private[this] var appliesBackPressure = false

  val observer: Observer[T] = new Observer[T] {
    def onNext(elem: T): Future[Ack] = lock.synchronized {
      if (!upstreamIsComplete && !downstreamIsDone) {
        try {
          queue.offer(elem)
          pushToConsumer()
        }
        catch {
          case NonFatal(ex) =>
            onError(ex)
            Cancel
        }
      }
      else {
        Cancel
      }
    }

    def onError(ex: Throwable) = lock.synchronized {
      if (!upstreamIsComplete && !downstreamIsDone) {
        errorThrown = ex
        upstreamIsComplete = true
        pushToConsumer()
      }
    }

    def onComplete() = lock.synchronized {
      if (!upstreamIsComplete && !downstreamIsDone) {
        upstreamIsComplete = true
        pushToConsumer()
      }
    }
  }

  private[this] def pushToConsumer(): Future[Ack] = {
    if (itemsToPush == 0) {
      nextAckPromise = Promise[Ack]()
      appliesBackPressure = false
      itemsToPush += 1

      scheduler.execute(new Runnable {
        def run() = fastLoop(0)
      })

      Continue
    }
    else if (appliesBackPressure) {
      itemsToPush += 1
      nextAckPromise.future
    }
    else if (itemsToPush >= bufferSize) {
      appliesBackPressure = true
      itemsToPush += 1
      nextAckPromise.future
    }
    else {
      itemsToPush += 1
      Continue
    }
  }

  private[this] def rescheduled(processed: Int): Unit = {
    fastLoop(processed)
  }

  @tailrec
  private[this] def fastLoop(processed: Int): Unit = {
    if (!downstreamIsDone) {
      // errors have priority, in case an error happened, it is streamed immediately
      val hasError = errorThrown ne null
      val next: T = queue.poll()

      if (next != null && !hasError)
        underlying.onNext(next) match {
          case sync if sync.isCompleted =>
            sync match {
              case continue if continue == Continue || continue.value.get == Continue.IsSuccess =>
                // process next
                fastLoop(processed + 1)

              case done if done == Cancel || done.value.get == Cancel.IsSuccess =>
                // ending loop
                downstreamIsDone = true
                lock.synchronized {
                  itemsToPush = 0
                  nextAckPromise.success(Cancel)
                }

              case error if error.value.get.isFailure =>
                // ending loop
                downstreamIsDone = true
                try underlying.onError(error.value.get.failed.get) finally
                  lock.synchronized {
                    itemsToPush = 0
                    nextAckPromise.success(Cancel)
                  }
            }

          case async =>
            async.onComplete {
              case Continue.IsSuccess =>
                // re-run loop (in different thread)
                rescheduled(processed + 1)

              case Cancel.IsSuccess =>
                // ending loop
                downstreamIsDone = true
                lock.synchronized {
                  itemsToPush = 0
                  nextAckPromise.success(Cancel)
                }

              case Failure(ex) =>
                // ending loop
                downstreamIsDone = true
                try underlying.onError(ex) finally
                  lock.synchronized {
                    itemsToPush = 0
                    nextAckPromise.success(Cancel)
                  }

              case other =>
                // never happens, but to appease Scala's compiler
                downstreamIsDone = true
                try underlying.onError(new MatchError(s"$other")) finally
                  lock.synchronized {
                    itemsToPush = 0
                    nextAckPromise.success(Cancel)
                  }
            }
        }
      else if (upstreamIsComplete || hasError) {
        // Race-condition check, but if upstreamIsComplete=true is visible, then the queue should be fully published
        // because there's a clear happens-before relationship between queue.offer() and upstreamIsComplete=true
        // NOTE: errors have priority, so in case of an error seen, then the loop is stopped
        if (!hasError && !queue.isEmpty) {
          fastLoop(processed) // re-run loop
        }
        else {
          // ending loop
          downstreamIsDone = true
          try {
            if (errorThrown ne null)
              underlying.onError(errorThrown)
            else
              underlying.onComplete()
          }
          finally lock.synchronized {
            queue.clear() // for GC purposes
            itemsToPush = 0
            nextAckPromise.success(Cancel)
          }
        }
      }
      else {
        val remaining = lock.synchronized {
          itemsToPush -= processed
          if (itemsToPush <= 0) // this really has to be LESS-or-equal
            nextAckPromise.success(Continue)
          itemsToPush
        }

        // if the queue is non-empty (i.e. concurrent modifications might have happened)
        // then start all over again
        if (remaining > 0) fastLoop(0)
      }
    }
  }
}

object BackPressuredBufferedSubscriber {
  def apply[T](observer: Observer[T], bufferSize: Int)(implicit s: Scheduler) =
    new BackPressuredBufferedSubscriber[T](observer, bufferSize)
}
