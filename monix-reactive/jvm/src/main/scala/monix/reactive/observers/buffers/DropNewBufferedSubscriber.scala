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
import monix.execution.atomic.{Atomic, AtomicInt}
import monix.execution.misc.NonFatal
import monix.reactive.observers.{BufferedSubscriber, Subscriber}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** A high-performance and non-blocking [[BufferedSubscriber]]
  * implementation for the [[monix.reactive.OverflowStrategy.DropNew DropNew]]
  * and the [[monix.reactive.OverflowStrategy.DropNewAndSignal DropNewAndSignal]]
  * overflow strategies.
  */
private[observers] final class DropNewBufferedSubscriber[A] private
  (out: Subscriber[A], bufferSize: Int, onOverflow: Long => Option[A] = null)
  extends CommonBufferMembers with BufferedSubscriber[A] with Subscriber.Sync[A] {

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  implicit val scheduler = out.scheduler
  private[this] val em = out.scheduler.executionModel

  private[this] val itemsToPush =
    Atomic.withPadding(0, LeftRight256)

  private[this] val droppedCount: AtomicInt =
    if (onOverflow != null) AtomicInt.withPadding(0, LeftRight128)
    else null

  private[this] val queue =
    ConcurrentQueue.limited[A](bufferSize)

  def onNext(elem: A): Ack = {
    if (upstreamIsComplete || downstreamIsComplete) Stop else {
      if (elem == null) {
        onError(new NullPointerException("Null not supported in onNext"))
        Stop
      }
      else {
        if (queue.offer(elem)) pushToConsumer()
        else if (onOverflow != null) droppedCount.increment()
        Continue
      }
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
      // Starting the run-loop, as at this point we can be sure
      // that no other loop is active
      scheduler.execute(consumerRunLoop)
    }
  }

  private[this] val consumerRunLoop = new Runnable {
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

    private def goAsync(next: A, ack: Future[Ack], processed: Int, toProcess: Int): Unit =
      ack.onComplete {
        case Success(Continue) =>
          val nextAck = signalNext(next)
          val isSync = ack == Continue || ack == Stop
          val nextFrame = if (isSync) em.nextFrameIndex(0) else 0
          fastLoop(nextAck, processed + toProcess, nextFrame)

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
        var streamErrors = true
        try {
          // The `processed` count is only for counting things processed
          // from the queue, but not overflow messages, as these are
          // not pushed to the queue - so we keep track of what to add
          var toProcess = 0
          val next = {
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
              toProcess = 1
              queue.poll()
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
                  goAsync(next, ack, processed, toProcess)
                  return
              }
            }
            else {
              goAsync(next, ack, processed, toProcess)
              return
            }
          }
          else if (upstreamIsComplete) {
            // Race-condition check, but if upstreamIsComplete=true is
            // visible, then the queue should be fully published because
            // there's a clear happens-before relationship between
            // queue.offer() and upstreamIsComplete=true
            if (queue.isEmpty && (onOverflow == null || droppedCount.get == 0)) {
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

private[observers] object DropNewBufferedSubscriber {
  /** Returns an instance of a [[DropNewBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropNew DropNew]]
    * overflowStrategy.
    */
  def simple[A](underlying: Subscriber[A], bufferSize: Int): DropNewBufferedSubscriber[A] =
    new DropNewBufferedSubscriber[A](underlying, bufferSize, null)

  /** Returns an instance of a [[DropNewBufferedSubscriber]] for the
    * [[monix.reactive.OverflowStrategy.DropNewAndSignal DropNewAndSignal]]
    * overflowStrategy.
    */
  def withSignal[A](underlying: Subscriber[A], bufferSize: Int, onOverflow: Long => Option[A]): DropNewBufferedSubscriber[A] =
    new DropNewBufferedSubscriber[A](underlying, bufferSize, onOverflow)
}

