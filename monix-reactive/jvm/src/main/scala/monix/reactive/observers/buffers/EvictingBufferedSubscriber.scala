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
import monix.execution.atomic.PaddingStrategy.{LeftRight128, LeftRight256}
import monix.execution.atomic.{Atomic, AtomicAny, AtomicInt}
import monix.execution.internal.math
import monix.execution.misc.NonFatal
import monix.reactive.OverflowStrategy._
import monix.reactive.observers.buffers.AbstractEvictingBufferedSubscriber._
import monix.reactive.observers.{BufferedSubscriber, Subscriber}

import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.util.{Failure, Success}

/** A [[BufferedSubscriber]] implementation for the
  * [[monix.reactive.OverflowStrategy.DropOld DropOld]]
  * and for the
  * [[monix.reactive.OverflowStrategy.ClearBuffer ClearBuffer]]
  * overflow strategies.
  */
private[observers] final class EvictingBufferedSubscriber[-A] private
  (out: Subscriber[A], strategy: Evicted[Nothing], onOverflow: Long => Option[A])
  extends AbstractEvictingBufferedSubscriber(out, strategy, onOverflow) {

  @volatile protected var p50, p51, p52, p53, p54, p55, p56, p57 = 5
  @volatile protected var q50, q51, q52, q53, q54, q55, q56, q57 = 5
}

private[observers] object EvictingBufferedSubscriber {
  /** Returns an instance of a [[EvictingBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropOld DropOld]]
    * overflow strategy.
    */
  def dropOld[A](underlying: Subscriber[A], bufferSize: Int): EvictingBufferedSubscriber[A] = {
    require(bufferSize > 1, "bufferSize must be a strictly positive number, bigger than 1")
    val maxCapacity = math.nextPowerOf2(bufferSize)
    new EvictingBufferedSubscriber[A](underlying, DropOld(maxCapacity), null)
  }

  /** Returns an instance of a [[EvictingBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropOld DropOld]]
    * overflow strategy, with signaling of the number of events that
    * were dropped.
    */
  def dropOldAndSignal[A](underlying: Subscriber[A],
    bufferSize: Int, onOverflow: Long => Option[A]): EvictingBufferedSubscriber[A] = {

    require(bufferSize > 1, "bufferSize must be a strictly positive number, bigger than 1")
    val maxCapacity = math.nextPowerOf2(bufferSize)
    new EvictingBufferedSubscriber[A](underlying, DropOld(maxCapacity), onOverflow)
  }

  /** Returns an instance of a [[EvictingBufferedSubscriber]] for the
    * [[monix.reactive.OverflowStrategy.ClearBuffer ClearBuffer]]
    * overflow strategy.
    */
  def clearBuffer[A](underlying: Subscriber[A], bufferSize: Int): EvictingBufferedSubscriber[A] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    require(bufferSize > 1, "bufferSize must be a strictly positive number, bigger than 1")
    val maxCapacity = math.nextPowerOf2(bufferSize)
    new EvictingBufferedSubscriber[A](underlying, ClearBuffer(maxCapacity), null)
  }

  /** Returns an instance of a [[EvictingBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.ClearBuffer ClearBuffer]]
    * overflow strategy, with signaling of the number of events that
    * were dropped.
    */
  def clearBufferAndSignal[A](underlying: Subscriber[A],
    bufferSize: Int, onOverflow: Long => Option[A]): EvictingBufferedSubscriber[A] = {

    require(bufferSize > 1, "bufferSize must be a strictly positive number, bigger than 1")
    val maxCapacity = math.nextPowerOf2(bufferSize)
    new EvictingBufferedSubscriber[A](underlying, ClearBuffer(maxCapacity), onOverflow)
  }
}

private[observers] abstract class AbstractEvictingBufferedSubscriber[-A]
  (out: Subscriber[A], strategy: Evicted[Nothing], onOverflow: Long => Option[A])
  extends CommonBufferMembers with BufferedSubscriber[A] with Subscriber.Sync[A] {

  require(strategy.bufferSize > 0, "bufferSize must be a strictly positive number")

  implicit val scheduler = out.scheduler
  private[this] val em = out.scheduler.executionModel

  private[this] val droppedCount: AtomicInt =
    if (onOverflow != null) AtomicInt.withPadding(0, LeftRight128)
    else null

  private[this] val itemsToPush =
    Atomic.withPadding(0, LeftRight256)
  private[this] val queue =
    new ConcurrentBuffer[A](strategy)

  def onNext(elem: A): Ack = {
    if (upstreamIsComplete || downstreamIsComplete) Stop else {
      if (elem == null) {
        onError(new NullPointerException("Null not supported in onNext"))
        Stop
      }
      else {
        val dropped = queue.offer(elem)
        if (dropped > 0 && onOverflow != null)
          droppedCount.increment(dropped)

        val increment = 1 - dropped
        pushToConsumer(increment)
        Continue
      }
    }
  }

  def onError(ex: Throwable): Unit = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      errorThrown = ex
      upstreamIsComplete = true
      pushToConsumer(1)
    }
  }

  def onComplete(): Unit = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      upstreamIsComplete = true
      pushToConsumer(1)
    }
  }

  private[this] def pushToConsumer(increment: Int): Unit = {
    val currentNr = {
      if (increment != 0)
        itemsToPush.getAndIncrement(increment)
      else
        itemsToPush.get
    }

    // If a run-loop isn't started, then go, go, go!
    if (currentNr == 0) {
      // Starting the run-loop, as at this point we can be sure
      // that no other loop is active
      scheduler.execute(consumerLoop)
    }
  }

  private[this] val consumerLoop = new Runnable {
    def run(): Unit = {
      // This lastIterationAck is also being set by the consumer-loop,
      // but it's important for the write to happen before `itemsToPush`,
      // to ensure its visibility
      fastLoop(Queue.empty, lastIterationAck, 0, 0)
    }

    private def signalNext(next: A): Future[Ack] =
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

    private def signalComplete(): Unit =
      try out.onComplete() catch {
        case NonFatal(ex) =>
          scheduler.reportFailure(ex)
      }

    private def signalError(ex: Throwable): Unit =
      try out.onError(ex) catch {
        case NonFatal(err) =>
          scheduler.reportFailure(err)
      }

    private def goAsync(prevQueue: Queue[A], next: A, ack: Future[Ack], processed: Int, toProcess: Int): Unit =
      ack.onComplete {
        case Success(Continue) =>
          val nextAck = signalNext(next)
          val isSync = ack == Continue || ack == Stop
          val nextFrame = if (isSync) em.nextFrameIndex(0) else 0
          fastLoop(prevQueue, nextAck, processed + toProcess, nextFrame)

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

    private def fastLoop(prevQueue: Queue[A], prevAck: Future[Ack], lastProcessed: Int, startIndex: Int): Unit = {
      var ack = if (prevAck == null) Continue else prevAck
      var isFirstIteration = ack == Continue
      var processed = lastProcessed
      var nextIndex = startIndex
      var currentQueue = prevQueue

      while (!downstreamIsComplete) {
        var streamErrors = true
        try {
          // Local cache
          if (currentQueue.isEmpty) currentQueue = queue.drain()
          // The `processed` count is only for counting things processed
          // from the queue, but not overflow messages, as these are
          // not pushed to the queue - so we keep track of what to add
          var toProcess = 0
          val next: A = {
            // Do we have an overflow message to send?
            val overflowMessage =
              if (onOverflow == null || droppedCount.get == 0)
                null.asInstanceOf[A]
              else
                onOverflow(droppedCount.getAndSet(0)) match {
                  case Some(value) => value
                  case None => null.asInstanceOf[A]
                }

            if (overflowMessage != null) overflowMessage else {
              if (currentQueue.isEmpty)
                null.asInstanceOf[A]
              else {
                val (ref,q) = currentQueue.dequeue
                currentQueue = q
                toProcess = 1
                ref
              }
            }
          }

          // Threshold after which we are no longer allowed to
          // stream errors downstream if they happen
          streamErrors = false

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
                    processed += toProcess
                  }

                case Stop =>
                  // ending loop
                  downstreamIsComplete = true
                  itemsToPush.set(0)
                  return

                case async =>
                  goAsync(currentQueue, next, ack, processed, toProcess)
                  return
              }
            }
            else {
              goAsync(currentQueue, next, ack, processed, toProcess)
              return
            }
          }
          else if (upstreamIsComplete) {
            // Race-condition check, but if upstreamIsComplete=true is
            // visible, then the queue should be fully published because
            // there's a clear happens-before relationship between
            // queue.offer() and upstreamIsComplete=true
            currentQueue = queue.drain()
            if (currentQueue.isEmpty && (onOverflow == null || droppedCount.get == 0)) {
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
        } catch {
          case NonFatal(ex) =>
            if (streamErrors) {
              // ending loop
              downstreamIsComplete = true
              itemsToPush.set(0)
              signalError(ex)
            } else {
              scheduler.reportFailure(ex)
            }
        }
      }
    }
  }
}

private[observers] object AbstractEvictingBufferedSubscriber {
  private final class ConcurrentBuffer[A](strategy: Evicted[Nothing]) {
    private[this] val bufferRef: AtomicAny[Buffer[A]] =
      AtomicAny.withPadding(emptyBuffer, LeftRight256)

    def drain(): Queue[A] = {
      val current = bufferRef.getAndSet(emptyBuffer)
      current.queue
    }

    def offer(a: A): Int = {
      val current = bufferRef.get
      val length = current.length
      val queue = current.queue

      if (length < strategy.bufferSize) {
        val update = Buffer(length+1, queue.enqueue(a))
        if (!bufferRef.compareAndSet(current, update))
          offer(a)
        else
          0
      } else {
        strategy match {
          case DropOld(_) | DropOldAndSignal(_, _) =>
            val (_, q) = queue.dequeue
            val update = Buffer(length, q.enqueue(a))
            if (!bufferRef.compareAndSet(current, update))
              offer(a)
            else
              1

          case ClearBuffer(_) | ClearBufferAndSignal(_, _) =>
            val update = Buffer(1, Queue(a))
            if (!bufferRef.compareAndSet(current, update))
              offer(a)
            else
              length

          case DropNew(_) | DropNewAndSignal(_, _) =>
            // The buffer for DropNew is specialized, so
            // we should never get this case
            1
        }
      }
    }
  }

  private final case class Buffer[+A](
    length: Int,
    queue: Queue[A])
  private final val emptyBuffer =
    Buffer(0, Queue.empty)
}