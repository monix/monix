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

import monifu.collection.mutable.ConcurrentQueue
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Subscriber}
import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.Failure
import scala.util.control.NonFatal

/**
 * A [[BufferedSubscriber]] implementation for the
 * [[monifu.reactive.OverflowStrategy.BackPressure BackPressured]] buffer overflowStrategy.
 */
final class BackPressuredBufferedSubscriber[-T] private
    (underlying: Subscriber[T], bufferSize: Int)
  extends BufferedSubscriber[T] { self =>

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  implicit val scheduler = underlying.scheduler
  private[this] val queue = ConcurrentQueue.empty[T]
  private[this] val batchSizeModulus = scheduler.env.batchSize - 1

  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  @volatile private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  @volatile private[this] var downstreamIsDone = false

  // for enforcing non-concurrent updates and back-pressure
  // all access must be synchronized
  private[this] val lock = new AnyRef
  private[this] var itemsToPush = 0
  private[this] var nextAckPromise = Promise[Ack]()
  private[this] var appliesBackPressure = false

  def onNext(elem: T): Future[Ack] = lock.synchronized {
    if (!upstreamIsComplete && !downstreamIsDone) {
      try {
        queue.offer(elem)
        pushToConsumer()
      }
      catch {
        case NonFatal(ex) =>
          onError(ex)
          Cancel
      }
    }
    else {
      Cancel
    }
  }

  def onError(ex: Throwable) = lock.synchronized {
    if (!upstreamIsComplete && !downstreamIsDone) {
      errorThrown = ex
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  def onComplete() = lock.synchronized {
    if (!upstreamIsComplete && !downstreamIsDone) {
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  private[this] def pushToConsumer(): Future[Ack] = {
    if (itemsToPush == 0) {
      nextAckPromise = Promise[Ack]()
      appliesBackPressure = false
      itemsToPush += 1

      scheduler.execute(new Runnable {
        def run() = fastLoop(0, 0)
      })

      Continue
    }
    else if (appliesBackPressure) {
      itemsToPush += 1
      nextAckPromise.future
    }
    else if (itemsToPush >= bufferSize) {
      appliesBackPressure = true
      itemsToPush += 1
      nextAckPromise.future
    }
    else {
      itemsToPush += 1
      Continue
    }
  }

  private[this] def rescheduled(processed: Int): Unit = {
    fastLoop(processed, 0)
  }

  @tailrec
  private[this] def fastLoop(processed: Int, syncIndex: Int): Unit = {
    if (!downstreamIsDone) {
      val hasError = errorThrown ne null
      val next: T = queue.poll()

      if (next != null) {
        val ack = underlying.onNext(next)
        val nextIndex = if (!ack.isCompleted) 0 else
          (syncIndex + 1) & batchSizeModulus

        if (nextIndex != 0) {
          if (ack == Continue || ack.value.get == Continue.IsSuccess)
            fastLoop(processed + 1, nextIndex)
          else if (ack == Cancel || ack.value.get == Cancel.IsSuccess) {
            // ending loop
            downstreamIsDone = true
            lock.synchronized {
              itemsToPush = 0
              nextAckPromise.success(Cancel)
            }
          }
          else if (ack.value.get.isFailure) {
            // ending loop
            downstreamIsDone = true
            try underlying.onError(ack.value.get.failed.get) finally
              lock.synchronized {
                itemsToPush = 0
                nextAckPromise.success(Cancel)
              }
          }
          else {
            // never happens
            downstreamIsDone = true
            try underlying.onError(new MatchError(s"${ack.value.get}")) finally
              lock.synchronized {
                itemsToPush = 0
                nextAckPromise.success(Cancel)
              }
          }
        }
        else ack.onComplete {
          case Continue.IsSuccess =>
            // re-run loop (in different thread)
            rescheduled(processed + 1)

          case Cancel.IsSuccess =>
            // ending loop
            downstreamIsDone = true
            lock.synchronized {
              itemsToPush = 0
              nextAckPromise.success(Cancel)
            }

          case Failure(ex) =>
            // ending loop
            downstreamIsDone = true
            try underlying.onError(ex) finally
              lock.synchronized {
                itemsToPush = 0
                nextAckPromise.success(Cancel)
              }

          case other =>
            // never happens, but to appease Scala's compiler
            downstreamIsDone = true
            try underlying.onError(new MatchError(s"$other")) finally
              lock.synchronized {
                itemsToPush = 0
                nextAckPromise.success(Cancel)
              }
        }
      }
      else if (upstreamIsComplete || hasError) {
        // Race-condition check, but if upstreamIsComplete=true is visible,
        // then the queue should be fully published because there's a clear
        // happens-before relationship between queue.offer() and upstreamIsComplete=true
        // NOTE: errors have priority, so in case of an error seen, then the loop is stopped
        if (!queue.isEmpty) {
          fastLoop(processed, syncIndex) // re-run loop
        }
        else {
          // ending loop
          downstreamIsDone = true
          try {
            if (errorThrown ne null)
              underlying.onError(errorThrown)
            else
              underlying.onComplete()
          }
          finally lock.synchronized {
            queue.clear() // for GC purposes
            itemsToPush = 0
            nextAckPromise.success(Cancel)
          }
        }
      }
      else {
        val remaining = lock.synchronized {
          itemsToPush -= processed
          if (itemsToPush <= 0) // this really has to be LESS-or-equal
            nextAckPromise.success(Continue)
          itemsToPush
        }

        // if the queue is non-empty (i.e. concurrent modifications might have happened)
        // then start all over again
        if (remaining > 0) fastLoop(0, syncIndex)
      }
    }
  }
}

object BackPressuredBufferedSubscriber {
  def apply[T](underlying: Subscriber[T], bufferSize: Int) =
    new BackPressuredBufferedSubscriber[T](underlying, bufferSize)
}
