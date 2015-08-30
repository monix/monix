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
import monifu.concurrent.atomic.padded.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Subscriber}
import scala.annotation.tailrec
import scala.util.Failure
import scala.util.control.NonFatal

/**
 * A [[BufferedSubscriber]] implementation for the
 * [[monifu.reactive.OverflowStrategy.DropNew DropNew]] overflow strategy.
 */
final class DropIncomingBufferedSubscriber[-T] private
    (underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T = null)
  extends BufferedSubscriber[T] with SynchronousSubscriber[T] { self =>

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  implicit val scheduler = underlying.scheduler
  private[this] val queue = ConcurrentQueue.empty[T]
  private[this] val batchSizeModulus = scheduler.env.batchSize - 1

  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  @volatile private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  @volatile private[this] var downstreamIsDone = false
  // for enforcing non-concurrent updates
  private[this] val itemsToPush = Atomic(0)
  private[this] var eventsDropped = 0L

  def onNext(elem: T): Ack = self.synchronized {
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
          // then try to send our current event
          // (recursive non-tailrec call)
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

  def onError(ex: Throwable) = self.synchronized {
    if (!upstreamIsComplete && !downstreamIsDone) {
      errorThrown = ex
      upstreamIsComplete = true
      notifyConsumerOfNewEvent()
    }
  }

  def onComplete(): Unit = self.synchronized {
    if (!upstreamIsComplete && !downstreamIsDone) {
      if (eventsDropped > 0 && onOverflow != null) try {
        val message = onOverflow(eventsDropped)
        eventsDropped = 0
        queue.offer(message)
        upstreamIsComplete = true
        notifyConsumerOfNewEvent()
      }
      catch {
        case NonFatal(ex) =>
          onError(ex)
      }
      else {
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
        def run() = fastLoop(0,0)
      })
  }

  private[this] def rescheduled(processed: Int): Unit = {
    fastLoop(processed, 0)
  }

  @tailrec
  private[this] def fastLoop(processed: Int, syncIndex: Int): Unit = {
    if (!downstreamIsDone) {
      val hasError = errorThrown ne null
      val next = queue.poll()

      if (next != null) {
        val ack = underlying.onNext(next)
        val nextIndex = if (!ack.isCompleted) 0 else
          (syncIndex + 1) & batchSizeModulus

        if (nextIndex != 0) {
          if (ack == Continue || ack.value.get == Continue.IsSuccess) {
            // process next
            fastLoop(processed + 1, nextIndex)
          }
          else if (ack == Cancel || ack.value.get == Cancel.IsSuccess) {
            // ending loop
            downstreamIsDone = true
            itemsToPush.set(0)
          }
          else if (ack.value.get.isFailure) {
            // ending loop
            downstreamIsDone = true
            itemsToPush.set(0)
            underlying.onError(ack.value.get.failed.get)
          }
          else {
            // never happens, but to appease the Scala compiler
            downstreamIsDone = true
            itemsToPush.set(0)
            underlying.onError(new MatchError(s"${ack.value.get}"))
          }
        }
        else ack.onComplete {
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
            // never happens, but to appease the Scala compiler
            downstreamIsDone = true
            itemsToPush.set(0)
            underlying.onError(new MatchError(s"$other"))
        }
      }
      else if (upstreamIsComplete || hasError) {
        // Race-condition check, but if upstreamIsComplete=true is visible,
        // then the queue should be fully published because there's a clear happens-before
        // relationship between queue.offer() and upstreamIsComplete=true
        if (!queue.isEmpty) {
          fastLoop(processed, syncIndex)
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
        if (remaining > 0) fastLoop(0, syncIndex)
      }
    }
  }
}

object DropIncomingBufferedSubscriber {
  /**
   * Returns an instance of a [[DropIncomingBufferedSubscriber]]
   * for the [[monifu.reactive.OverflowStrategy.DropNew DropNew]]
   * overflowStrategy.
   */
  def simple[T](underlying: Subscriber[T], bufferSize: Int): DropIncomingBufferedSubscriber[T] = {
    new DropIncomingBufferedSubscriber[T](underlying, bufferSize, null)
  }

  /**
   * Returns an instance of a [[DropIncomingBufferedSubscriber]]
   * for the [[monifu.reactive.OverflowStrategy.DropNew DropNew]]
   * overflowStrategy.
   */
  def withSignal[T](underlying: Subscriber[T], bufferSize: Int,
    onOverflow: Long => T): DropIncomingBufferedSubscriber[T] = {

    new DropIncomingBufferedSubscriber[T](underlying, bufferSize, onOverflow)
  }
}