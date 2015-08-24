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

package monifu.reactive.observers

import monifu.collection.mutable.{DropAllOnOverflowQueue, EvictingQueue, DropHeadOnOverflowQueue}
import monifu.concurrent.Scheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Observer}

import scala.annotation.tailrec
import scala.util.Failure
import scala.util.control.NonFatal

/**
 * A [[BufferedSubscriber]] implementation for the following policies:
 *
 * - [[monifu.reactive.OverflowStrategy.DropNew]]
 * - [[monifu.reactive.OverflowStrategy.DropNewThenSignal]]
 */
final class EvictingBufferedSubscriber[-T] private
    (underlying: Observer[T], buffer: EvictingQueue[AnyRef], onOverflow: Long => T = null)
    (implicit val scheduler: Scheduler)
  extends BufferedSubscriber[T] with SynchronousSubscriber[T] { self =>

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
  private[this] val consumerBuffer = new Array[AnyRef](200)

  val observer: SynchronousObserver[T] = new SynchronousObserver[T] {
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

    def onError(ex: Throwable) = self.synchronized {
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
  }

  /**
   * Starts the consumer loop, in case there is none active.
   */
  private[this] def consume(): Unit = {
    // Checks if there are pending elements and starts
    // consuming.
    def nextBatch(): Unit = self.synchronized {
      val count =
        if (eventsDropped > 0 && onOverflow != null) {
          val message = onOverflow(eventsDropped).asInstanceOf[AnyRef]
          eventsDropped = 0
          consumerBuffer(0) = message
          1 + buffer.pollMany(consumerBuffer, 1)
        }
        else
          buffer.pollMany(consumerBuffer)

      if (count > 0) {
        isLoopStarted = true
        scheduler.execute(fastLoop(consumerBuffer, count, 0))
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
              case continue if continue == Continue || continue.value.get == Continue.IsSuccess =>
                // process next
                fastLoop(array, arrayLength, processed + 1)

              case done if done == Cancel || done.value.get == Cancel.IsSuccess =>
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
              case Continue.IsSuccess =>
                // re-run loop (in different thread)
                loop(array, arrayLength, processed + 1)

              case Cancel.IsSuccess =>
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
      else
        // consume next batch of items
        nextBatch()
    }

    // no synchronization here, because we are calling
    // this on the producer's side, which is already synchronized
    if (!isLoopStarted) {
      isLoopStarted = true
      nextBatch()
    }
  }
}

object EvictingBufferedSubscriber {
  /**
   * Returns an instance of a [[EvictingBufferedSubscriber]]
   * for the [[monifu.reactive.OverflowStrategy.DropOld DropIDropOldcoming]]
   * overflowStrategy.
   */
  def dropOld[T](underlying: Observer[T], bufferSize: Int)
    (implicit s: Scheduler): EvictingBufferedSubscriber[T] = {

    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize)
    new EvictingBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[EvictingBufferedSubscriber]] for the
   * [[monifu.reactive.OverflowStrategy.DropOldThenSignal DropOldThenSignal]]
   * overflowStrategy.
   */
  def dropOld[T](underlying: Observer[T], bufferSize: Int, onOverflow: Long => T)
    (implicit s: Scheduler): EvictingBufferedSubscriber[T] = {

    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropHeadOnOverflowQueue[AnyRef](bufferSize)
    new EvictingBufferedSubscriber[T](underlying, buffer, onOverflow)
  }

  /**
   * Returns an instance of a [[EvictingBufferedSubscriber]] for the
   * [[monifu.reactive.OverflowStrategy.ClearBuffer ClearBuffer]]
   * overflowStrategy.
   */
  def clearBuffer[T](underlying: Observer[T], bufferSize: Int)
    (implicit s: Scheduler): EvictingBufferedSubscriber[T] = {

    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize)
    new EvictingBufferedSubscriber[T](underlying, buffer, null)
  }

  /**
   * Returns an instance of a [[EvictingBufferedSubscriber]]
   * for the [[monifu.reactive.OverflowStrategy.ClearBuffer ClearBuffer]]
   * overflowStrategy.
   */
  def clearBuffer[T](underlying: Observer[T], bufferSize: Int, onOverflow: Long => T)
    (implicit s: Scheduler): EvictingBufferedSubscriber[T] = {

    require(bufferSize > 1,
      "bufferSize must be a strictly positive number, bigger than 1")

    val buffer = DropAllOnOverflowQueue[AnyRef](bufferSize)
    new EvictingBufferedSubscriber[T](underlying, buffer, onOverflow)
  }
}