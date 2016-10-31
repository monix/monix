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

package monix.reactive.observers.buffers

import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.internal.Platform
import monix.execution.internal.math.nextPowerOf2
import monix.reactive.exceptions.BufferOverflowException
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import org.jctools.queues.{MessagePassingQueue, MpscArrayQueue, MpscUnboundedArrayQueue}
import scala.annotation.tailrec
import scala.util.Failure
import scala.util.control.NonFatal

/** A highly optimized [[BufferedSubscriber]] implementation for
  * [[monix.reactive.OverflowStrategy.Unbounded OverflowStrategy.Unbounded]].
  *
  * Internally it uses an unbounded array-linked queue that grows
  * in chunks of size `Platform.recommendedBatchSize`. It has no limit
  * to how much it can grow, therefore it should be used with care, since
  * it can eat your whole heap memory.
  *
  * @param underlying is the underlying observer receiving the queued events
  */
private[buffers] final class SimpleBufferedSubscriber[A] private
  (underlying: Subscriber[A], _qRef: MessagePassingQueue[A])
  extends CommonBufferQueue[A](_qRef)
  with BufferedSubscriber[A] with Subscriber.Sync[A] {

  private[this] val em = underlying.scheduler.executionModel
  implicit val scheduler = underlying.scheduler

  def onNext(elem: A): Ack = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      try {
        if (queue.offer(elem)) {
          pushToConsumer()
          Continue
        }
        else {
          onError(new BufferOverflowException(
            s"Downstream observer is too slow, buffer overflowed with a " +
            s"specified maximum capacity of ${queue.capacity()}"
          ))

          Stop
        }
      }
      catch {
        case NonFatal(ex) =>
          onError(ex)
          Stop
      }
    }
    else
      Stop
  }

  def onError(ex: Throwable): Unit = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      errorThrown = ex
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  def onComplete(): Unit = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  private[this] def pushToConsumer(): Unit = {
    val currentNr = itemsToPush.getAndIncrement()

    // If a run-loop isn't started, then go, go, go!
    if (currentNr == 0)
      scheduler.execute(new Runnable {
        def run() = fastLoop(0,0)
      })
  }

  private[this] def rescheduled(processed: Int): Unit = {
    fastLoop(processed, 0)
  }

  @tailrec
  private[this] def fastLoop(processed: Int, syncIndex: Int): Unit = {
    if (!downstreamIsComplete) {
      val hasError = errorThrown ne null
      val next = queue.relaxedPoll()

      if (next != null) {
        val ack = underlying.onNext(next)
        val nextIndex = if (!ack.isCompleted) 0 else
          em.nextFrameIndex(syncIndex)

        if (nextIndex > syncIndex) {
          if (ack == Continue || ack.value.get == Continue.AsSuccess) {
            // process next
            fastLoop(processed + 1, nextIndex)
          }
          else if (ack == Stop || ack.value.get == Stop.AsSuccess) {
            // ending loop
            downstreamIsComplete = true
            itemsToPush.set(0)
          }
          else if (ack.value.get.isFailure) {
            // ending loop
            downstreamIsComplete = true
            itemsToPush.set(0)
            underlying.onError(ack.value.get.failed.get)
          }
          else {
            // never happens
            downstreamIsComplete = true
            itemsToPush.set(0)
            underlying.onError(new MatchError(ack.value.get.toString))
          }
        }
        else ack.onComplete {
          case Continue.AsSuccess =>
            // re-run loop (in different thread)
            rescheduled(processed + 1)

          case Stop.AsSuccess =>
            // ending loop
            downstreamIsComplete = true
            itemsToPush.set(0)

          case Failure(ex) =>
            // ending loop
            downstreamIsComplete = true
            itemsToPush.set(0)
            underlying.onError(ex)

          case other =>
            // never happens, but to appease the Scala compiler
            downstreamIsComplete = true
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
          downstreamIsComplete = true
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

private[monix] object SimpleBufferedSubscriber {
  def unbounded[T](underlying: Subscriber[T]): Subscriber.Sync[T] = {
    val queue = new MpscUnboundedArrayQueue[T](Platform.recommendedBatchSize)
    new SimpleBufferedSubscriber[T](underlying, queue)
  }

  def overflowTriggering[A](underlying: Subscriber[A], bufferSize: Int): Subscriber.Sync[A] = {
    val maxCapacity = math.max(4, nextPowerOf2(bufferSize))
    val queue = new MpscArrayQueue[A](maxCapacity)
    new SimpleBufferedSubscriber[A](underlying, queue)
  }
}
