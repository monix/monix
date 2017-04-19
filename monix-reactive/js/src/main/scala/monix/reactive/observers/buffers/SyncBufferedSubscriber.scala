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
import monix.execution.internal.collection.{ArrayQueue, _}
import monix.execution.misc.NonFatal
import monix.reactive.exceptions.BufferOverflowException
import monix.reactive.observers.{BufferedSubscriber, Subscriber}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** A [[BufferedSubscriber]] implementation for the
  * [[monix.reactive.OverflowStrategy.DropNew DropNew]] overflow strategy.
  */
private[observers] final class SyncBufferedSubscriber[-A] private
  (out: Subscriber[A], queue: EvictingQueue[A], onOverflow: Long => Option[A] = null)
  extends BufferedSubscriber[A] with Subscriber.Sync[A] {

  implicit val scheduler = out.scheduler
  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  private[this] var downstreamIsComplete = false
  // represents an indicator that there's a loop in progress
  private[this] var isLoopActive = false
  // events being dropped
  private[this] var droppedCount = 0L
  // last acknowledgement received by consumer loop
  private[this] var lastIterationAck: Future[Ack] = Continue
  // Used on the consumer side to split big synchronous workloads in batches
  private[this] val em = scheduler.executionModel

  def onNext(elem: A): Ack = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      if (elem == null) {
        onError(new NullPointerException("Null not supported in onNext"))
        Stop
      }
      else try {
        droppedCount += queue.offer(elem)
        consume()
        Continue
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
      consume()
    }
  }

  def onComplete(): Unit = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      upstreamIsComplete = true
      consume()
    }
  }

  private def consume(): Unit =
    if (!isLoopActive) {
      isLoopActive = true
      scheduler.execute(consumerRunLoop)
    }

  private[this] val consumerRunLoop = new Runnable {
    def run(): Unit = {
      fastLoop(lastIterationAck, 0)
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
            downstreamSignalComplete(ex)
            Stop
          case None =>
            ack
        }
      } catch {
        case NonFatal(ex) =>
          downstreamSignalComplete(ex)
          Stop
      }

    private def downstreamSignalComplete(ex: Throwable = null): Unit = {
      downstreamIsComplete = true
      try {
        if (ex != null) out.onError(ex)
        else out.onComplete()
      } catch {
        case NonFatal(err) =>
          scheduler.reportFailure(err)
      }
    }

    private def goAsync(next: A, ack: Future[Ack]): Unit =
      ack.onComplete {
        case Success(Continue) =>
          val nextAck = signalNext(next)
          val isSync = ack == Continue || ack == Stop
          val nextFrame = if (isSync) em.nextFrameIndex(0) else 0
          fastLoop(nextAck, nextFrame)

        case Success(Stop) =>
          // ending loop
          downstreamIsComplete = true
          isLoopActive = false

        case Failure(ex) =>
          // ending loop
          isLoopActive = false
          downstreamSignalComplete(ex)
      }

    private def fastLoop(prevAck: Future[Ack], startIndex: Int): Unit = {
      var ack = if (prevAck == null) Continue else prevAck
      var isFirstIteration = ack == Continue
      var nextIndex = startIndex

      while (isLoopActive && !downstreamIsComplete) {
        var streamErrors = true
        try {
          val next = {
            // Do we have an overflow message to send?
            val overflowMessage =
              if (onOverflow == null || droppedCount == 0)
                null.asInstanceOf[A]
              else {
                val msg = onOverflow(droppedCount) match {
                  case Some(value) => value
                  case None => null.asInstanceOf[A]
                }

                droppedCount = 0
                msg
              }

            if (overflowMessage != null) overflowMessage else
              queue.poll()
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
                    isLoopActive = false
                    return
                  } else {
                    val isSync = ack == Continue
                    nextIndex = if (isSync) em.nextFrameIndex(nextIndex) else 0
                  }

                case Stop =>
                  // ending loop
                  downstreamIsComplete = true
                  isLoopActive = false
                  return

                case async =>
                  goAsync(next, ack)
                  return
              }
            }
            else {
              goAsync(next, ack)
              return
            }
          }
          else {
            if (upstreamIsComplete) downstreamSignalComplete(errorThrown)
            // ending loop
            lastIterationAck = ack
            isLoopActive = false
            return
          }
        } catch {
          case NonFatal(ex) =>
            if (streamErrors) {
              // ending loop
              downstreamSignalComplete(ex)
              isLoopActive = false
              return
            } else {
              scheduler.reportFailure(ex)
              return
            }
        }
      }
    }
  }
}

private[monix] object SyncBufferedSubscriber {
  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropNew DropNew]]
    * overflow strategy.
    */
  def unbounded[T](underlying: Subscriber[T]): Subscriber.Sync[T] = {
    val buffer = ArrayQueue.unbounded[T]
    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropNew DropNew]]
    * overflow strategy.
    */
  def bounded[T](underlying: Subscriber[T], bufferSize: Int): Subscriber.Sync[T] = {
    require(bufferSize > 1, "bufferSize must be strictly higher than 1")
    val buffer = ArrayQueue.bounded[T](bufferSize, capacity => {
      BufferOverflowException.build(
        s"Downstream observer is too slow, buffer over capacity with a " +
          s"specified buffer size of $bufferSize")
    })

    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropNew DropNew]]
    * overflow strategy.
    */
  def dropNew[T](underlying: Subscriber[T], bufferSize: Int): Subscriber.Sync[T] = {
    require(bufferSize > 1, "bufferSize must be strictly higher than 1")
    val buffer = ArrayQueue.bounded[T](bufferSize)
    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropNew DropNew]]
    * overflow strategy.
    */
  def dropNewAndSignal[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => Option[T]): Subscriber.Sync[T] = {
    require(bufferSize > 1, "bufferSize must be strictly higher than 1")
    val buffer = ArrayQueue.bounded[T](bufferSize)
    new SyncBufferedSubscriber[T](underlying, buffer, onOverflow)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropOld DropOld]]
    * overflow strategy.
    */
  def dropOld[T](underlying: Subscriber[T], bufferSize: Int): Subscriber.Sync[T] = {
    require(bufferSize > 1, "bufferSize must be strictly higher than 1")
    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.DropOld DropOld]]
    * overflow strategy, with signaling of the number of events that
    * were dropped.
    */
  def dropOldAndSignal[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => Option[T]): Subscriber.Sync[T] = {
    require(bufferSize > 1, "bufferSize must be strictly higher than 1")
    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SyncBufferedSubscriber[T](underlying, buffer, onOverflow)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]] for the
    * [[monix.reactive.OverflowStrategy.ClearBuffer ClearBuffer]]
    * overflow strategy.
    */
  def clearBuffer[T](underlying: Subscriber[T], bufferSize: Int): Subscriber.Sync[T] = {
    require(bufferSize > 1, "bufferSize must be strictly higher than 1")
    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.reactive.OverflowStrategy.ClearBuffer ClearBuffer]]
    * overflow strategy, with signaling of the number of events that
    * were dropped.
    */
  def clearBufferAndSignal[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => Option[T]): Subscriber.Sync[T] = {
    require(bufferSize > 1, "bufferSize must be strictly higher than 1")
    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SyncBufferedSubscriber[T](underlying, buffer, onOverflow)
  }
}
