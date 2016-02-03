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
import monix.streams.observers.{BufferedSubscriber, SyncSubscriber}
import scala.annotation.tailrec
import scala.util.Failure
import scala.util.control.NonFatal

/**
 * A [[BufferedSubscriber]] implementation for the
 * [[OverflowStrategy.DropNew DropNew]] overflow strategy.
 */
private[buffers] final class EvictingBufferedSubscriber[-T] private
  (underlying: Subscriber[T], buffer: EvictingQueue[AnyRef], onOverflow: Long => T = null)
  extends BufferedSubscriber[T] with SyncSubscriber[T] { self =>

  implicit val scheduler = underlying.scheduler
  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  @volatile private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  @volatile private[this] var downstreamIsDone = false
  // MUST BE synchronized with `self`, represents an
  // indicator that there's a loop in progress
  private[this] var isLoopStarted = false
  // events being dropped
  private[this] var eventsDropped = 0L
  // MUST only be accessed within the consumer loop
  private[this] val consumerBuffer = new Array[AnyRef](Platform.recommendedBatchSize)

  def onNext(elem: T): Ack = self.synchronized {
    if (!upstreamIsComplete && !downstreamIsDone) {
      try {
        eventsDropped += buffer.offer(elem.asInstanceOf[AnyRef])
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

  def onError(ex: Throwable): Unit = self.synchronized {
    if (!upstreamIsComplete && !downstreamIsDone) {
      errorThrown = ex
      upstreamIsComplete = true
      consume()
    }
  }

  def onComplete(): Unit = self.synchronized {
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

  private[this] val consumer = new Runnable { consumer =>
    def run(): Unit = self.synchronized {
      val count =
        if (eventsDropped > 0 && onOverflow != null) {
          try {
            val message = onOverflow(eventsDropped).asInstanceOf[AnyRef]
            eventsDropped = 0
            consumerBuffer(0) = message
            1 + buffer.pollMany(consumerBuffer, 1)
          }
          catch {
            case NonFatal(ex) =>
              errorThrown = ex
              upstreamIsComplete = true
              0
          }
        }
        else {
          buffer.pollMany(consumerBuffer)
        }

      if (count > 0) {
        isLoopStarted = true
        fastLoop(consumerBuffer, count, 0)
      }
      else if (upstreamIsComplete || (errorThrown ne null)) {
        // ending loop
        downstreamIsDone = true
        isLoopStarted = false

        if (errorThrown ne null)
          underlying.onError(errorThrown)
        else
          underlying.onComplete()
      }
      else {
        isLoopStarted = false
      }
    }

    def loop(array: Array[AnyRef], arrayLength: Int, processed: Int): Unit = {
      fastLoop(array, arrayLength, processed)
    }

    @tailrec
    def fastLoop(array: Array[AnyRef], arrayLength: Int, processed: Int): Unit = {
      if (processed < arrayLength) {
        val next = array(processed)

        underlying.onNext(next.asInstanceOf[T]) match {
          case sync if sync.isCompleted =>
            sync match {
              case continue if continue == Continue || continue.value.get == Continue.AsSuccess =>
                // process next
                fastLoop(array, arrayLength, processed + 1)

              case done if done == Cancel || done.value.get == Cancel.AsSuccess =>
                self.synchronized {
                  // ending loop
                  downstreamIsDone = true
                  isLoopStarted = false
                }

              case error if error.value.get.isFailure =>
                try underlying.onError(error.value.get.failed.get) finally
                  self.synchronized {
                    // ending loop
                    downstreamIsDone = true
                    isLoopStarted = false
                  }
            }

          case async =>
            async.onComplete {
              case Continue.AsSuccess =>
                // re-run loop (in different thread)
                loop(array, arrayLength, processed + 1)

              case Cancel.AsSuccess =>
                self.synchronized {
                  // ending loop
                  downstreamIsDone = true
                  isLoopStarted = false
                }

              case Failure(ex) =>
                try underlying.onError(ex) finally {
                  // ending loop
                  downstreamIsDone = true
                  isLoopStarted = false
                }

              case other =>
                try underlying.onError(new MatchError(s"$other")) finally
                  self.synchronized {
                    // never happens, but to appease the Scala compiler
                    downstreamIsDone = true
                    isLoopStarted = false
                  }
            }
        }
      }
      else {
        // consume next batch of items
        scheduler.execute(consumer)
      }
    }
  }
}

private[monix] object EvictingBufferedSubscriber {
  /**
   * Returns an instance of a [[EvictingBufferedSubscriber]]
   * for the [[monix.streams.OverflowStrategy.DropOld DropOld]]
   * overflow strategy.
   */
  def dropOld[T](underlying: Subscriber[T], bufferSize: Int): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize)
    new EvictingBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[EvictingBufferedSubscriber]]
   * for the [[monix.streams.OverflowStrategy.DropOld DropOld]]
   * overflow strategy, with signaling of the number of events that
   * were dropped.
   */
  def dropOld[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize)
    new EvictingBufferedSubscriber[T](underlying, buffer, onOverflow)
  }

  /**
   * Returns an instance of a [[EvictingBufferedSubscriber]] for the
   * [[monix.streams.OverflowStrategy.ClearBuffer ClearBuffer]]
   * overflow strategy.
   */
  def clearBuffer[T](underlying: Subscriber[T], bufferSize: Int): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize)
    new EvictingBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[EvictingBufferedSubscriber]]
   * for the [[monix.streams.OverflowStrategy.ClearBuffer ClearBuffer]]
   * overflow strategy, with signaling of the number of events that
   * were dropped.
   */
  def clearBuffer[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SyncSubscriber[T] = {
    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize)
    new EvictingBufferedSubscriber[T](underlying, buffer, onOverflow)
  }
}
