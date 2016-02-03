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

import monix.execution.internal.Platform
import monix.execution.internal.collection.{EvictingQueue, DropHeadOnOverflowQueue, DropAllOnOverflowQueue}
import monix.streams.{Subscriber, OverflowStrategy, Ack}
import monix.streams.Ack.{Cancel, Continue}
import monix.streams.exceptions.BufferOverflowException
import monix.execution.internal.collection.ArrayQueue
import monix.streams.observers.{BufferedSubscriber, SyncSubscriber}
import scala.annotation.tailrec
import scala.util.control.NonFatal

/**
  * A [[BufferedSubscriber]] implementation for the
  * [[OverflowStrategy.DropNew DropNew]] overflow strategy.
  */
private[buffers] final class SyncBufferedSubscriber[-T] private
(underlying: Subscriber[T], buffer: EvictingQueue[T], onOverflow: Long => T = null)
  extends BufferedSubscriber[T] with SyncSubscriber[T] {

  implicit val scheduler = underlying.scheduler
  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  private[this] var downstreamIsDone = false
  // represents an indicator that there's a loop in progress
  private[this] var isLoopStarted = false
  // events being dropped
  private[this] var eventsDropped = 0L
  // Used on the consumer side to split big synchronous workloads in batches
  private[this] val batchSizeModulus = Platform.recommendedBatchSize - 1

  def onNext(elem: T): Ack = {
    if (!upstreamIsComplete && !downstreamIsDone) {
      try {
        eventsDropped += buffer.offer(elem)
        consume()
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
      consume()
    }
  }

  def onComplete(): Unit = {
    if (!upstreamIsComplete && !downstreamIsDone) {
      upstreamIsComplete = true
      consume()
    }
  }

  private[this] def consume() = {
    // no synchronization here, because we are calling
    // this on the producer's side, which is already synchronized
    if (!isLoopStarted) {
      isLoopStarted = true
      scheduler.execute(consumer)
    }
  }

  private[this] val consumer: Runnable = new Runnable {
    def run(): Unit = {
      fastLoop(0)
    }

    @inline
    private[this] def downstreamSignalComplete(ex: Throwable = null): Unit = {
      downstreamIsDone = true
      if (ex != null)
        underlying.onError(ex)
      else
        underlying.onComplete()
    }

    @tailrec
    private[this] def fastLoop(syncIndex: Int): Unit = {
      val nextEvent =
        if (eventsDropped > 0 && onOverflow != null) {
          try {
            val message = onOverflow(eventsDropped).asInstanceOf[AnyRef]
            eventsDropped = 0
            message
          }
          catch {
            case NonFatal(ex) =>
              errorThrown = ex
              upstreamIsComplete = true
              null
          }
        }
        else {
          buffer.poll()
        }

      if (nextEvent != null) {
        val next = nextEvent.asInstanceOf[T]
        val ack = underlying.onNext(next)

        // for establishing whether the next call is asynchronous,
        // note that the check with batchSizeModulus is meant for splitting
        // big synchronous loops in smaller batches
        val nextIndex = if (!ack.isCompleted) 0 else
          (syncIndex + 1) & batchSizeModulus

        if (nextIndex > 0) {
          if (ack == Continue || ack.value.get == Continue.AsSuccess)
            fastLoop(nextIndex) // process next
          else {
            // ending loop
            val ex = ack.value.get.failed.getOrElse(new MatchError(ack.value.get))
            downstreamSignalComplete(ex)
          }
        }
        else ack.onComplete {
          case Continue.AsSuccess =>
            // re-run loop (in different thread)
            run()

          case Cancel.AsSuccess =>
            // ending loop
            downstreamIsDone = true

          case failure =>
            // ending loop
            val ex = failure.failed.getOrElse(new MatchError(failure))
            downstreamSignalComplete(ex)
        }
      }
      else {
        if (upstreamIsComplete) downstreamSignalComplete(errorThrown)
        // ending loop
        isLoopStarted = false
      }
    }
  }
}

private[monix] object SyncBufferedSubscriber {
  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.streams.OverflowStrategy.DropNew DropNew]]
    * overflow strategy.
    */
  def unbounded[T](underlying: Subscriber[T]): SyncSubscriber[T] = {
    val buffer = ArrayQueue.unbounded[T]
    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.streams.OverflowStrategy.DropNew DropNew]]
    * overflow strategy.
    */
  def bounded[T](underlying: Subscriber[T], bufferSize: Int): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = ArrayQueue.bounded[T](bufferSize, capacity => {
      new BufferOverflowException(
        s"Downstream observer is too slow, buffer over capacity with a " +
          s"specified buffer size of $bufferSize")
    })

    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.streams.OverflowStrategy.DropNew DropNew]]
    * overflow strategy.
    */
  def dropNew[T](underlying: Subscriber[T], bufferSize: Int): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = ArrayQueue.bounded[T](bufferSize)
    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.streams.OverflowStrategy.DropNew DropNew]]
    * overflow strategy.
    */
  def dropNew[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = ArrayQueue.bounded[T](bufferSize)
    new SyncBufferedSubscriber[T](underlying, buffer, onOverflow)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.streams.OverflowStrategy.DropOld DropOld]]
    * overflow strategy.
    */
  def dropOld[T](underlying: Subscriber[T], bufferSize: Int): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.streams.OverflowStrategy.DropOld DropOld]]
    * overflow strategy, with signaling of the number of events that
    * were dropped.
    */
  def dropOld[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SyncBufferedSubscriber[T](underlying, buffer, onOverflow)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]] for the
    * [[monix.streams.OverflowStrategy.ClearBuffer ClearBuffer]]
    * overflow strategy.
    */
  def clearBuffer[T](underlying: Subscriber[T], bufferSize: Int): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SyncBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
    * Returns an instance of a [[SyncBufferedSubscriber]]
    * for the [[monix.streams.OverflowStrategy.ClearBuffer ClearBuffer]]
    * overflow strategy, with signaling of the number of events that
    * were dropped.
    */
  def clearBuffer[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SyncBufferedSubscriber[T](underlying, buffer, onOverflow)
  }
}
