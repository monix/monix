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

package monifu.internals.operators

import monifu.Ack.Continue
import monifu.internals._
import monifu.{Ack, Observable, Observer}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}


private[monifu] object buffer {
  /**
   * Implementation for [[Observable.buffer]].
   */
  def skipped[T](source: Observable[T], count: Int, skip: Int): Observable[Seq[T]] = {
    require(count > 0, "count must be strictly positive")
    require(skip > 0, "skip must be strictly positive")

    Observable.create { subscriber =>
      import subscriber.{scheduler => s}
      
      source.onSubscribe(new Observer[T] {
        private[this] val shouldDrop = skip > count
        private[this] var leftToDrop = 0
        private[this] val shouldOverlap = skip < count
        private[this] var nextBuffer = ArrayBuffer.empty[T]
        private[this] var buffer = null : ArrayBuffer[T]
        private[this] var size = 0

        def onNext(elem: T): Future[Ack] = {
          if (shouldDrop && leftToDrop > 0) {
            leftToDrop -= 1
            Continue
          }
          else {
            if (buffer == null) {
              buffer = nextBuffer
              size = nextBuffer.length
              nextBuffer = ArrayBuffer.empty[T]
            }

            size += 1
            buffer.append(elem)
            if (shouldOverlap && size - skip > 0) nextBuffer += elem

            if (size >= count) {
              if (shouldDrop) leftToDrop = skip - count
              val continue = subscriber.onNext(buffer)
              buffer = null
              continue
            }
            else
              Continue
          }
        }

        def onError(ex: Throwable): Unit = {
          if (buffer != null) {
            subscriber.onNext(buffer).onContinueSignalError(subscriber, ex)
            buffer = null
            nextBuffer = null
          }
          else
            subscriber.onError(ex)
        }

        def onComplete(): Unit = {
          if (buffer != null) {
            subscriber.onNext(buffer).onContinueSignalComplete(subscriber)
            buffer = null
            nextBuffer = null
          }
          else
            subscriber.onComplete()
        }
      })
    }
  }

  /**
   * Implementation for [[Observable.buffer]].
   */
  def timed[T](source: Observable[T], timespan: FiniteDuration, maxCount: Int): Observable[Seq[T]] = {
    require(timespan >= Duration.Zero, "timespan must be positive")
    require(maxCount >= 0, "maxCount must be positive")

    Observable.create[Seq[T]] { subscriber =>
      implicit val s = subscriber.scheduler

      source.onSubscribe(new Observer[T] {
        private[this] val timespanMillis = timespan.toMillis
        private[this] var buffer = ArrayBuffer.empty[T]
        private[this] var expiresAt = s.currentTimeMillis() + timespanMillis

        def onNext(elem: T) = {
          val rightNow = s.currentTimeMillis()
          buffer.append(elem)

          if (expiresAt <= rightNow || (maxCount > 0 && maxCount <= buffer.length)) {
            val oldBuffer = buffer
            buffer = ArrayBuffer.empty[T]
            expiresAt = rightNow + timespanMillis
            subscriber.onNext(oldBuffer)
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          if (buffer.nonEmpty) {
            subscriber.onNext(buffer).onContinueSignalError(subscriber, ex)
            buffer = null
          }
          else
            subscriber.onError(ex)
        }

        def onComplete(): Unit = {
          if (buffer.nonEmpty) {
            subscriber.onNext(buffer).onContinueSignalComplete(subscriber)
            buffer = null
          }
          else
            subscriber.onComplete()
        }
      })
    }
  }
}
