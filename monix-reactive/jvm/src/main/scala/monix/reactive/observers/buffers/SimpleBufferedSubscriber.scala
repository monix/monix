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
import monix.execution.atomic.Atomic
import monix.execution.atomic.PaddingStrategy.LeftRight256
import monix.execution.internal.math.nextPowerOf2
import monix.execution.misc.NonFatal
import monix.reactive.exceptions.BufferOverflowException
import monix.reactive.observers.{BufferedSubscriber, Subscriber}

import scala.concurrent.Future
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
private[observers] final class SimpleBufferedSubscriber[A] protected
  (out: Subscriber[A], _qRef: ConcurrentQueue[A], capacity: Int)
  extends AbstractSimpleBufferedSubscriber[A](out, _qRef, capacity) {

  @volatile protected var p50, p51, p52, p53, p54, p55, p56, p57 = 5
  @volatile protected var q50, q51, q52, q53, q54, q55, q56, q57 = 5
}

private[observers] abstract class AbstractSimpleBufferedSubscriber[A] protected
  (out: Subscriber[A], _qRef: ConcurrentQueue[A], capacity: Int)
  extends CommonBufferMembers with BufferedSubscriber[A] with Subscriber.Sync[A] {

  private[this] val queue = _qRef
  private[this] val em = out.scheduler.executionModel
  implicit val scheduler = out.scheduler
  private[this] val itemsToPush =
    Atomic.withPadding(0, LeftRight256)

  def onNext(elem: A): Ack = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      if (elem == null) {
        onError(new NullPointerException("Null not supported in onNext"))
        Stop
      }
      else try {
        if (queue.offer(elem)) {
          pushToConsumer()
          Continue
        }
        else {
          onError(BufferOverflowException.build(
            s"Downstream observer is too slow, buffer overflowed with a " +
            s"specified maximum capacity of $capacity"
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
    if (currentNr == 0) {
      // Starting the run-loop, as at this point we can be sure
      // that no other loop is active, or if there is one, then
      // it is shutting down!
      scheduler.execute(consumerLoop)
    }
  }

  private[this] val consumerLoop = new Runnable {
    def run(): Unit = {
      // This lastIterationAck is also being set by the consumer-loop,
      // but it's important for the write to happen before `itemsToPush`,
      // to ensure its visibility
      fastLoop(lastIterationAck, 0, 0)
    }

    private final def signalNext(next: A): Future[Ack] =
      try {
        val ack = out.onNext(next)
        // Tries flattening the Future[Ack] to a
        // synchronous value
        if (ack == Continue || ack == Stop)
          ack
        else ack.value match {
          case Some(Success(success)) =>
            success
          case Some(Failure(ex)) =>
            signalError(ex)
            Stop
          case None =>
            ack
        }
      } catch {
        case NonFatal(ex) =>
          signalError(ex)
          Stop
      }

    private final def signalComplete(): Unit =
      try out.onComplete() catch {
        case NonFatal(ex) =>
          scheduler.reportFailure(ex)
      }

    private final def signalError(ex: Throwable): Unit =
      try out.onError(ex) catch {
        case NonFatal(err) =>
          scheduler.reportFailure(err)
      }

    private def goAsync(next: A, ack: Future[Ack], processed: Int): Unit =
      ack.onComplete {
        case Success(Continue) =>
          val nextAck = signalNext(next)
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
          signalError(ex)
      }

    private def fastLoop(prevAck: Future[Ack], lastProcessed: Int, startIndex: Int): Unit = {
      var ack = if (prevAck == null) Continue else prevAck
      var isFirstIteration = ack == Continue
      var processed = lastProcessed
      var nextIndex = startIndex

      while (!downstreamIsComplete) {
        val next = queue.poll()

        if (next != null) {
          if (nextIndex > 0 || isFirstIteration) {
            isFirstIteration = false

            ack match {
              case Continue =>
                ack = signalNext(next)
                if (ack == Stop) {
                  // ending loop
                  downstreamIsComplete = true
                  itemsToPush.set(0)
                  return
                } else {
                  val isSync = ack == Continue
                  nextIndex = if (isSync) em.nextFrameIndex(nextIndex) else 0
                  processed += 1
                }

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

            if (errorThrown ne null) signalError(errorThrown)
            else signalComplete()
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
          if (remaining <= 0) return
        }
      }
    }
  }
}

private[observers] object SimpleBufferedSubscriber {
  def unbounded[A](underlying: Subscriber[A]): SimpleBufferedSubscriber[A] = {
    val queue = ConcurrentQueue.unbounded[A](isBatched = true)
    new SimpleBufferedSubscriber[A](underlying, queue, Int.MaxValue)
  }

  def overflowTriggering[A](underlying: Subscriber[A], bufferSize: Int): SimpleBufferedSubscriber[A] = {
    val maxCapacity = math.max(4, nextPowerOf2(bufferSize))
    val queue = ConcurrentQueue.limited[A](bufferSize)
    new SimpleBufferedSubscriber[A](underlying, queue, maxCapacity)
  }
}
