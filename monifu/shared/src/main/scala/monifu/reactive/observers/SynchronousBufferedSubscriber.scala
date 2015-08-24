/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.observers

import monifu.collection.mutable.ConcurrentQueue
import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.padded.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{BufferOverflowException, Ack, Observer}

import scala.annotation.tailrec
import scala.util.Failure
import scala.util.control.NonFatal

/**
 * A highly optimized [[BufferedSubscriber]] implementation. It supports 2
 * [[monifu.reactive.OverflowStrategy buffer policies]] - unbounded or bounded and terminated
 * with a [[monifu.reactive.BufferOverflowException BufferOverflowException]].
 *
 * To create an instance using an unbounded overflowStrategy: {{{
 *   // by default, the constructor for BufferedSubscriber is returning this unbounded variant
 *   BufferedSubscriber(observer)
 *
 *   // or you can specify the Unbounded overflowStrategy explicitly
 *   import monifu.reactive.OverflowStrategy.Unbounded
 *   val buffered = BufferedSubscriber(observer, overflowStrategy = Unbounded)
 * }}}
 *
 * To create a bounded buffered observable that triggers
 * [[monifu.reactive.BufferOverflowException BufferOverflowException]]
 * when over capacity: {{{
 *   import monifu.reactive.OverflowStrategy.OverflowTriggering
 *   // triggers buffer overflow error after 10000 messages
 *   val buffered = BufferedSubscriber(observer, overflowStrategy = OverflowTriggering(bufferSize = 10000))
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
      val hasError = errorThrown ne null
      val next = queue.poll()

      if (next != null) {
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
        // Race-condition check, but if upstreamIsComplete=true is visible,
        // then the queue should be fully published because there's a clear happens-before
        // relationship between queue.offer() and upstreamIsComplete=true
        if (!queue.isEmpty) {
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
