/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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
import monifu.reactive.{Ack, Observer}

import scala.annotation.tailrec
import scala.util.Failure
import scala.util.control.NonFatal

/**
 * A [[BufferedSubscriber]] implementation for the following policies:
 *
 * - [[monifu.reactive.BufferPolicy.DropIncoming]]
 * - [[monifu.reactive.BufferPolicy.DropIncomingThenSignal]]
 */
final class DropIncomingBufferedSubscriber[-T] private
    (underlying: Observer[T], bufferSize: Int, onOverflow: Long => T = null)
    (implicit val scheduler: Scheduler)
  extends BufferedSubscriber[T] with SynchronousSubscriber[T] {

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  private[this] val queue = ConcurrentQueue.empty[T]
  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  @volatile private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  @volatile private[this] var downstreamIsDone = false
  // for enforcing non-concurrent updates
  private[this] val itemsToPush = Atomic(0)

  private[this] var eventsDropped = 0L

  val observer: SynchronousObserver[T] = new SynchronousObserver[T] {
    def onNext(elem: T): Ack = {
      if (!upstreamIsComplete && !downstreamIsDone) {
        if (itemsToPush.get >= bufferSize) {
          // no more room, dropping event
          eventsDropped += 1
          Continue
        }
        else if (eventsDropped > 0 && onOverflow != null) {
          try {
            val message = onOverflow(eventsDropped)
            eventsDropped = 0
            // first send the overflow message
            queue.offer(message)
            notifyConsumerOfNewEvent()
            // then try to send our current event (recursive call)
            onNext(elem)
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Cancel
          }
        }
        else try {
          queue.offer(elem)
          notifyConsumerOfNewEvent()
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
        notifyConsumerOfNewEvent()
      }
    }

    def onComplete() = {
      if (!upstreamIsComplete && !downstreamIsDone) {
        upstreamIsComplete = true
        notifyConsumerOfNewEvent()
      }
    }
  }

  @tailrec
  private[this] def notifyConsumerOfNewEvent(): Unit = {
    val currentNr = itemsToPush.get

    if (!itemsToPush.compareAndSet(currentNr, currentNr + 1))
      notifyConsumerOfNewEvent()
    else if (currentNr == 0)
      scheduler.execute(new Runnable {
        def run() = fastLoop(0)
      })
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

object DropIncomingBufferedSubscriber {
  /**
   * Returns an instance of a [[DropIncomingBufferedSubscriber]]
   * for the [[monifu.reactive.BufferPolicy.DropIncoming DropIncoming]]
   * policy.
   */
  def simple[T](underlying: Observer[T], bufferSize: Int)
      (implicit s: Scheduler): DropIncomingBufferedSubscriber[T] = {

    new DropIncomingBufferedSubscriber[T](underlying, bufferSize, null)
  }

  /**
   * Returns an instance of a [[DropIncomingBufferedSubscriber]]
   * for the [[monifu.reactive.BufferPolicy.DropIncoming DropIncoming]]
   * policy.
   */
  def withSignal[T](underlying: Observer[T], bufferSize: Int, onOverflow: Long => T)
      (implicit s: Scheduler): DropIncomingBufferedSubscriber[T] = {

    new DropIncomingBufferedSubscriber[T](underlying, bufferSize, onOverflow)
  }
}