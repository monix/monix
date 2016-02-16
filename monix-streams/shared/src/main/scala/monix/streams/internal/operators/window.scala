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

package monix.streams.internal.operators

import monix.execution.Ack
import monix.execution.Ack.{Cancel, Continue}
import monix.streams.{Observable, Observer}
import monix.streams.broadcast.ReplayProcessor
import monix.streams.internal._
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}

private[monix] object window {
  /** Implementation for [[Observable.window]] */
  def skipped[T](source: Observable[T], count: Int, skip: Int): Observable[Observable[T]] = {
    require(count > 0, "count must be strictly positive")
    require(skip > 0, "skip must be strictly positive")

    if (count == skip)
      sizedFixed(source, count)
    else if (skip < count)
      sizedOverlap(source, count, skip)
    else // skip > count
      sizedDrop(source, count, skip)
  }

  private def sizedFixed[T](source: Observable[T], count: Int) = {
    require(count > 0, "count must be strictly positive")

    Observable.unsafeCreate[Observable[T]] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var isDone = false
        private[this] var buffer = ReplayProcessor[T]()
        private[this] var ack = subscriber.onNext(buffer)
        private[this] var leftToPush = count

        def onNext(elem: T): Future[Ack] =
          if (isDone) Cancel
          else {
            if (leftToPush > 0) {
              leftToPush -= 1
              buffer.onNext(elem)
            }
            else {
              buffer.onComplete()
              buffer = ReplayProcessor(elem)
              leftToPush = count - 1

              val previousAck = ack
              ack = ack.onContinueStreamOnNext(subscriber, buffer)
              previousAck
            }
          }

        def onError(ex: Throwable): Unit = {
          if (!isDone) {
            isDone = true
            buffer.onComplete()
            ack.onContinueSignalError(subscriber, ex)
            buffer = null
          }
        }

        def onComplete(): Unit = {
          if (!isDone) {
            isDone = true
            buffer.onComplete()
            ack.onContinueSignalComplete(subscriber)
            buffer = null
          }
        }
      })
    }
  }

  def sizedOverlap[T](source: Observable[T], count: Int, skip: Int): Observable[Observable[T]] = {
    require(count > 0, "count must be strictly positive")
    require(skip > 0, "skip must be strictly positive")
    assert(skip < count, "skip < count")

    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var isDone = false
        private[this] var buffer = ReplayProcessor[T]()
        private[this] var ack = subscriber.onNext(buffer)
        private[this] var leftToPush = count

        private[this] val overlap = count - skip
        private[this] val queue = mutable.ArrayBuffer.empty[T]

        def onNext(elem: T): Future[Ack] =
          if (isDone) Cancel else {
            if (leftToPush > 0) {
              if (leftToPush <= overlap) queue += elem
              leftToPush -= 1
              buffer.onNext(elem)
            }
            else {
              buffer.onComplete()
              queue += elem
              buffer = ReplayProcessor(queue:_*)
              queue.clear()
              leftToPush = count - (overlap + 1)
              if (leftToPush <= overlap) queue += elem

              val previousAck = ack
              ack = ack.onContinueStreamOnNext(subscriber, buffer)
              previousAck
            }
          }

        def onError(ex: Throwable): Unit = {
          if (!isDone) {
            isDone = true
            buffer.onComplete()
            ack.onContinueSignalError(subscriber, ex)
            buffer = null
          }
        }

        def onComplete(): Unit = {
          if (!isDone) {
            isDone = true
            buffer.onComplete()
            ack.onContinueSignalComplete(subscriber)
            buffer = null
          }
        }
      })
    }
  }

  private def sizedDrop[T](source: Observable[T], count: Int, skip: Int): Observable[Observable[T]] = {
    require(count > 0, "count must be strictly positive")
    require(skip > 0, "skip must be strictly positive")
    assert(skip > count, "skip > drop")

    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var isDone = false
        private[this] var buffer = ReplayProcessor[T]()
        private[this] var ack = subscriber.onNext(buffer)
        private[this] var leftToPush = count
        private[this] var leftToDrop = 0

        def onNext(elem: T): Future[Ack] =
          if (isDone) Cancel else {
            if (leftToDrop > 0) {
              leftToDrop -= 1
              Continue
            }
            else if (leftToPush > 0) {
              leftToPush -= 1
              buffer.onNext(elem)
            }
            else {
              buffer.onComplete()
              leftToDrop = skip - count - 1
              leftToPush = count
              buffer = ReplayProcessor()

              val previousAck = ack
              ack = ack.onContinueStreamOnNext(subscriber, buffer)
              previousAck
            }
          }

        def onError(ex: Throwable): Unit = {
          if (!isDone) {
            isDone = true
            buffer.onComplete()
            ack.onContinueSignalError(subscriber, ex)
            buffer = null
          }
        }

        def onComplete(): Unit = {
          if (!isDone) {
            isDone = true
            buffer.onComplete()
            ack.onContinueSignalComplete(subscriber)
            buffer = null
          }
        }
      })
    }
  }

  /**
   * Implementation for [[Observable.window]].
   */
  def timed[T](source: Observable[T], timespan: FiniteDuration, maxCount: Int): Observable[Observable[T]] = {
    require(timespan >= Duration.Zero, "timespan must be positive")
    require(maxCount >= 0, "maxCount must be positive")

    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] val timespanMillis = timespan.toMillis
        private[this] var expiresAt = s.currentTimeMillis() + timespanMillis

        private[this] var size = 0
        private[this] var isDone = false
        private[this] var buffer = ReplayProcessor[T]()
        private[this] var ack = subscriber.onNext(buffer)

        def onNext(elem: T): Future[Ack] =
          if (isDone) Cancel else {
            val rightNow = s.currentTimeMillis()
            val hasExpired = expiresAt <= rightNow ||
              (maxCount > 0 && size >= maxCount)

            if (!hasExpired) {
              size += 1
              buffer.onNext(elem)
            }
            else {
              buffer.onComplete()
              buffer = ReplayProcessor(elem)
              expiresAt = rightNow + timespanMillis
              size = 1

              val previousAck = ack
              ack = ack.onContinueStreamOnNext(subscriber, buffer)
              previousAck
            }
          }

        def onError(ex: Throwable): Unit = {
          if (!isDone) {
            isDone = true
            buffer.onComplete()
            ack.onContinueSignalError(subscriber, ex)
            buffer = null
          }
        }

        def onComplete(): Unit = {
          if (!isDone) {
            isDone = true
            buffer.onComplete()
            ack.onContinueSignalComplete(subscriber)
            buffer = null
          }
        }
      })
    }
  }
}
