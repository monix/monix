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
import monix.execution.atomic.AtomicAny
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.internal.Platform
import monix.execution.internal.math._
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import org.jctools.queues.{MessagePassingQueue, MpscArrayQueue, MpscChunkedArrayQueue, MpscLinkedQueue}

import scala.concurrent.{Future, Promise}
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
private[buffers] final class BackPressuredBufferedSubscriber[A] private
  (out: Subscriber[A], _qRef: MessagePassingQueue[A])
  extends CommonBufferQueue[A](_qRef)
  with BufferedSubscriber[A] {

  private[this] val em = out.scheduler.executionModel
  implicit val scheduler = out.scheduler

  /** Whenever the primary queue is full, we still have
    * to enqueue the incoming messages somewhere. This
    * secondary queue gets used whenever the data-source
    * starts being back-pressured.
    */
  private[this] val secondaryQueue =
    MpscLinkedQueue.newMpscLinkedQueue[A]()

  /** A promise that becomes non-null whenever the queue is
    * full, to be completed whenever the queue is empty again.
    */
  private[this] val backPressured: AtomicAny[Promise[Ack]] =
    AtomicAny.withPadding(null, LeftRight128)

  def onNext(elem: A): Future[Ack] = {
    if (upstreamIsComplete || downstreamIsComplete) Stop else
      backPressured.get match {
        case null =>
          if (queue.offer(elem)) {
            pushToConsumer()
            Continue
          } else {
            val promise = Promise[Ack]()
            if (!backPressured.compareAndSet(null, promise))
              onNext(elem)
            else {
              secondaryQueue.offer(elem)
              pushToConsumer()
              promise.future
            }
          }
        case promise =>
          secondaryQueue.offer(elem)
          pushToConsumer()
          promise.future
      }
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
    if (currentNr == 0) {
      // It's important that lastIterationAck gets read after
      // we read from itemsToPush, in order to ensure its visibility
      val lastAck = lastIterationAck
      // Starting the run-loop, as at this point we can be sure
      // that no other loop is active
      scheduler.executeAsync(() => fastLoop(lastAck, 0, 0))
    }
  }

  private def goAsync(next: A, ack: Future[Ack], processed: Int): Unit =
    ack.onComplete {
      case Success(Continue) =>
        val nextAck = out.onNext(next)
        val isSync = ack == Continue || ack == Stop
        val nextFrame = if (isSync) em.nextFrameIndex(0) else 0
        fastLoop(nextAck, processed+1, nextFrame)

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
    @inline def stopStreaming(): Unit = {
      downstreamIsComplete = true
      val bp = backPressured.get
      if (bp != null) bp.success(Stop)
      itemsToPush.set(0)
    }

    var ack = if (prevAck == null) Continue else prevAck
    var isFirstIteration = ack == Continue
    var processed = lastProcessed
    var nextIndex = startIndex

    while (!downstreamIsComplete) {
      val next = {
        val ref = queue.relaxedPoll()
        if (ref != null) ref else
          secondaryQueue.poll()
      }

      if (next != null) {
        if (nextIndex > 0 || isFirstIteration) {
          isFirstIteration = false

          ack match {
            case Continue =>
              ack = out.onNext(next)
              if (ack == Stop) {
                stopStreaming()
                return
              } else {
                val isSync = ack == Continue
                nextIndex = if (isSync) em.nextFrameIndex(nextIndex) else 0
                processed += 1
              }

            case Stop =>
              stopStreaming()
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
          stopStreaming()
          if (errorThrown ne null) out.onError(errorThrown)
          else out.onComplete()
          return
        }
      }
      else {
        // Given we are writing in `itemsToPush` before this
        // assignment, it means that writes will not get reordered,
        // so when we observe that itemsToPush is zero on the
        // producer side, we will also have the latest lastIterationAck
        lastIterationAck = ack
        val remaining = itemsToPush.decrementAndGet(processed)

        processed = 0
        // if the queue is non-empty (i.e. concurrent modifications
        // just happened) then continue loop, otherwise stop
        if (remaining <= 0) {
          val bp = backPressured.getAndSet(null)
          if (bp != null) bp.success(Continue)
          return
        }
      }
    }
  }
}

object BackPressuredBufferedSubscriber {
  def apply[A](underlying: Subscriber[A], bufferSize: Int): BackPressuredBufferedSubscriber[A] = {
    val maxCapacity = math.max(4, nextPowerOf2(bufferSize))
    val queue = if (maxCapacity <= Platform.recommendedBatchSize)
      new MpscArrayQueue[A](maxCapacity)
    else {
      val initialCapacity = math.min(Platform.recommendedBatchSize, maxCapacity / 2)
      new MpscChunkedArrayQueue[A](initialCapacity, maxCapacity)
    }

    new BackPressuredBufferedSubscriber[A](underlying, queue)
  }
}