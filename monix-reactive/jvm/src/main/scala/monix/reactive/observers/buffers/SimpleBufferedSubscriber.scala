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
import org.jctools.queues.{MessagePassingQueue, MpscArrayQueue, MpscChunkedArrayQueue, MpscUnboundedArrayQueue}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** A highly optimized [[BufferedSubscriber]] implementation. It supports 2
  * [[monix.reactive.OverflowStrategy overflow strategies]]:
  *
  *   - [[monix.reactive.OverflowStrategy.Unbounded Unbounded]]
  *   - [[monix.reactive.OverflowStrategy.Fail Fail]]
  *
  * For the `Unbounded` strategy it uses an unbounded array-linked queue
  * that grows in chunks of size `Platform.recommendedBatchSize`.
  * It has no limit to how much it can grow, therefore it should be
  * used with care, since it can eat the whole heap memory.
  */
private[buffers] final class SimpleBufferedSubscriber[A] private
  (out: Subscriber[A], _qRef: MessagePassingQueue[A])
  extends CommonBufferQueue[A](_qRef)
  with BufferedSubscriber[A] with Subscriber.Sync[A] {

  private[this] val em = out.scheduler.executionModel
  implicit val scheduler = out.scheduler

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
        def run() = fastLoop(Continue, 0, 0)
      })
  }

  private def goAsync(next: A, ack: Future[Ack], processed: Int): Unit =
    ack.onComplete {
      case Success(Continue) =>
        val nextAck = out.onNext(next)
        val isSync = ack == Continue || ack == Stop
        val idx = if (isSync) em.nextFrameIndex(0) else 0
        fastLoop(nextAck, processed+1, idx)

      case Success(Stop) =>
        // ending loop
        downstreamIsComplete = true
        itemsToPush.set(0)

      case Failure(ex) =>
        // ending loop
        downstreamIsComplete = true
        itemsToPush.set(0)
        out.onError(ex)
    }

  private def fastLoop(prevAck: Future[Ack], lastProcessed: Int, startIndex: Int): Unit = {
    var ack = prevAck
    var processed = lastProcessed
    var isFirstIteration = true
    var nextIndex = startIndex

    while (!downstreamIsComplete) {
      val next = queue.relaxedPoll()

      if (next != null) {
        if (nextIndex > 0 || isFirstIteration) {
          isFirstIteration = false

          ack match {
            case Continue =>
              ack = out.onNext(next)
              val isSync = ack == Continue || ack == Stop
              nextIndex = if (isSync) em.nextFrameIndex(nextIndex) else 0
              processed += 1

            case Stop =>
              // ending loop
              downstreamIsComplete = true
              itemsToPush.set(0)
              return

            case async =>
              goAsync(next, ack, processed)
              return
          }
        }
        else {
          goAsync(next, ack, processed)
          return
        }
      }
      else if (upstreamIsComplete) {
        // Race-condition check, but if upstreamIsComplete=true is
        // visible, then the queue should be fully published because
        // there's a clear happens-before relationship between
        // queue.offer() and upstreamIsComplete=true
        if (queue.isEmpty) {
          // ending loop
          downstreamIsComplete = true
          itemsToPush.set(0)

          if (errorThrown ne null) out.onError(errorThrown)
          else out.onComplete()
          return
        }
      }
      else {
        val remaining = itemsToPush.decrementAndGet(processed)
        processed = 0
        // if the queue is non-empty (i.e. concurrent modifications
        // just happened) then continue loop, otherwise stop
        if (remaining <= 0) return
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
    val queue = if (maxCapacity <= Platform.recommendedBatchSize)
      new MpscArrayQueue[A](maxCapacity)
    else {
      val initialCapacity = math.min(Platform.recommendedBatchSize, maxCapacity / 2)
      new MpscChunkedArrayQueue[A](initialCapacity, maxCapacity)
    }

    new SimpleBufferedSubscriber[A](underlying, queue)
  }
}
