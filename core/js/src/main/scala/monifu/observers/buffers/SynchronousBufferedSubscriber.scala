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

package monifu.observers.buffers

import monifu.internal.collection.{EvictingQueue, DropHeadOnOverflowQueue, DropAllOnOverflowQueue}
import monifu.Ack.{Cancel, Continue}
import monifu.exceptions.BufferOverflowException
import monifu.internal.collection.ArrayQueue
import monifu.observers.{BufferedSubscriber, SynchronousSubscriber}
import monifu.{Ack, Subscriber}
import scala.annotation.tailrec
import scala.util.control.NonFatal

/**
 * A [[BufferedSubscriber]] implementation for the
 * [[monifu.OverflowStrategy.DropNew DropNew]] overflow strategy.
 */
private[buffers] final class SynchronousBufferedSubscriber[-T] private
  (underlying: Subscriber[T], buffer: EvictingQueue[T], onOverflow: Long => T = null)
  extends BufferedSubscriber[T] with SynchronousSubscriber[T] {

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
  private[this] val batchSizeModulus = scheduler.env.batchSize - 1

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

  def onError(ex: Throwable) = {
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
          if (ack == Continue || ack.value.get == Continue.IsSuccess)
            fastLoop(nextIndex) // process next
          else {
            // ending loop
            val ex = ack.value.get.failed.getOrElse(new MatchError(ack.value.get))
            downstreamSignalComplete(ex)
          }
        }
        else ack.onComplete {
          case Continue.IsSuccess =>
            // re-run loop (in different thread)
            run()

          case Cancel.IsSuccess =>
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

private[monifu] object SynchronousBufferedSubscriber {
  /**
   * Returns an instance of a [[SynchronousBufferedSubscriber]]
   * for the [[monifu.OverflowStrategy.DropNew DropNew]]
   * overflow strategy.
   */
  def unbounded[T](underlying: Subscriber[T]): SynchronousSubscriber[T] = {
    val buffer = ArrayQueue.unbounded[T]
    new SynchronousBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[SynchronousBufferedSubscriber]]
   * for the [[monifu.OverflowStrategy.DropNew DropNew]]
   * overflow strategy.
   */
  def bounded[T](underlying: Subscriber[T], bufferSize: Int): SynchronousSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = ArrayQueue.bounded[T](bufferSize, capacity => {
      new BufferOverflowException(
        s"Downstream observer is too slow, buffer over capacity with a " +
        s"specified buffer size of $bufferSize")
    })

    new SynchronousBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[SynchronousBufferedSubscriber]]
   * for the [[monifu.OverflowStrategy.DropNew DropNew]]
   * overflow strategy.
   */
  def dropNew[T](underlying: Subscriber[T], bufferSize: Int): SynchronousSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = ArrayQueue.bounded[T](bufferSize)
    new SynchronousBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[SynchronousBufferedSubscriber]]
   * for the [[monifu.OverflowStrategy.DropNew DropNew]]
   * overflow strategy.
   */
  def dropNew[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SynchronousSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = ArrayQueue.bounded[T](bufferSize)
    new SynchronousBufferedSubscriber[T](underlying, buffer, onOverflow)
  }
  
  /**
   * Returns an instance of a [[SynchronousBufferedSubscriber]]
   * for the [[monifu.OverflowStrategy.DropOld DropOld]]
   * overflow strategy.
   */
  def dropOld[T](underlying: Subscriber[T], bufferSize: Int): SynchronousSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SynchronousBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[SynchronousBufferedSubscriber]]
   * for the [[monifu.OverflowStrategy.DropOld DropOld]]
   * overflow strategy, with signaling of the number of events that
   * were dropped.
   */
  def dropOld[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SynchronousSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SynchronousBufferedSubscriber[T](underlying, buffer, onOverflow)
  }

  /**
   * Returns an instance of a [[SynchronousBufferedSubscriber]] for the
   * [[monifu.OverflowStrategy.ClearBuffer ClearBuffer]]
   * overflow strategy.
   */
  def clearBuffer[T](underlying: Subscriber[T], bufferSize: Int): SynchronousSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SynchronousBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[SynchronousBufferedSubscriber]]
   * for the [[monifu.OverflowStrategy.ClearBuffer ClearBuffer]]
   * overflow strategy, with signaling of the number of events that
   * were dropped.
   */
  def clearBuffer[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SynchronousSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize).asInstanceOf[EvictingQueue[T]]
    new SynchronousBufferedSubscriber[T](underlying, buffer, onOverflow)
  }
}