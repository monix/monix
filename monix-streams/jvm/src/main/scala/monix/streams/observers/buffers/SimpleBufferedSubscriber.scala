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

package monix.streams.observers.buffers

import java.util.concurrent.ConcurrentLinkedQueue
import monix.streams.{Subscriber, OverflowStrategy, Ack}
import monix.streams.Ack.{Cancel, Continue}
import monix.execution.internal.Platform
import monix.streams.exceptions.BufferOverflowException
import monix.streams.observers.{BufferedSubscriber, SyncSubscriber}
import scala.annotation.tailrec
import scala.util.Failure
import scala.util.control.NonFatal
import org.sincron.atomic.Atomic

/**
 * A highly optimized [[BufferedSubscriber]] implementation. It supports 2
 * [[OverflowStrategy overflow strategies]]:
 *
 *   - [[monix.streams.OverflowStrategy.Unbounded Unbounded]]
 *   - [[monix.streams.OverflowStrategy.Fail Fail]]
 *
 * @param underlying is the underlying observer receiving the queued events
 * @param bufferSize is the maximum buffer size, or zero if unbounded
 */
private[buffers] final class SimpleBufferedSubscriber[-T] private
  (underlying: Subscriber[T], bufferSize: Int = 0)
  extends BufferedSubscriber[T] with SyncSubscriber[T] { self =>

  require(bufferSize >= 0, "bufferSize must be a positive number")

  implicit val scheduler = underlying.scheduler
  private[this] val queue = new ConcurrentLinkedQueue[T]()
  private[this] val batchSizeModulus = Platform.recommendedBatchSize - 1

  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  @volatile private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  @volatile private[this] var downstreamIsDone = false
  // for enforcing non-concurrent updates
  private[this] val itemsToPush = Atomic(0)

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

  def onError(ex: Throwable): Unit = {
    if (!upstreamIsComplete && !downstreamIsDone) {
      errorThrown = ex
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  def onComplete(): Unit = {
    if (!upstreamIsComplete && !downstreamIsDone) {
      upstreamIsComplete = true
      pushToConsumer()
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
          def run() = fastLoop(0,0)
        })
    }
    else {
      // triggering overflow branch
      if (currentNr >= bufferSize && !upstreamIsComplete) {
        self.onError(new BufferOverflowException(
          s"Downstream observer is too slow, buffer over capacity with a " +
          s"specified buffer size of $bufferSize"))
      }
      else if (!itemsToPush.compareAndSet(currentNr, currentNr + 1))
        pushToConsumer()
      else if (currentNr == 0)
        scheduler.execute(new Runnable {
          def run() = fastLoop(0,0)
        })
    }
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

        if (nextIndex > 0) {
          if (ack == Continue || ack.value.get == Continue.AsSuccess) {
            // process next
            fastLoop(processed + 1, nextIndex)
          }
          else if (ack == Cancel || ack.value.get == Cancel.AsSuccess) {
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
            // never happens
            downstreamIsDone = true
            itemsToPush.set(0)
            underlying.onError(new MatchError(ack.value.get.toString))
          }
        }
        else ack.onComplete {
          case Continue.AsSuccess =>
            // re-run loop (in different thread)
            rescheduled(processed + 1)

          case Cancel.AsSuccess =>
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

private[monix] object SimpleBufferedSubscriber {
  def unbounded[T](underlying: Subscriber[T]): SyncSubscriber[T] =
    new SimpleBufferedSubscriber[T](underlying)

  def overflowTriggering[T](underlying: Subscriber[T], bufferSize: Int): SyncSubscriber[T] =
    new SimpleBufferedSubscriber[T](underlying, bufferSize)
}
