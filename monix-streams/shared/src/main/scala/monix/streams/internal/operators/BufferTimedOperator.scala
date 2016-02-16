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
import monix.execution.Ack.Continue
import monix.streams.ObservableLike.Operator
import monix.streams.observers.Subscriber

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}

private[streams] final class BufferTimedOperator[A](timespan: FiniteDuration, maxCount: Int)
  extends Operator[A, Seq[A]] {

  require(timespan >= Duration.Zero, "timespan must be positive")
  require(maxCount >= 0, "maxCount must be positive")

  def apply(out: Subscriber[Seq[A]]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var ack: Future[Ack] = Continue
      private[this] val timespanMillis = timespan.toMillis
      private[this] var buffer = ArrayBuffer.empty[A]
      private[this] var expiresAt = scheduler.currentTimeMillis() + timespanMillis

      def onNext(elem: A) = {
        val rightNow = scheduler.currentTimeMillis()
        buffer.append(elem)

        if (expiresAt <= rightNow || (maxCount > 0 && maxCount <= buffer.length)) {
          val oldBuffer = buffer
          buffer = ArrayBuffer.empty[A]
          expiresAt = rightNow + timespanMillis
          ack = out.onNext(oldBuffer)
        } else {
          ack = Continue
        }

        ack
      }

      def onError(ex: Throwable): Unit = {
        buffer = null
        out.onError(ex)
      }

      def onComplete(): Unit = {
        // In case the last onNext isn't finished, then
        // we need to apply back-pressure, otherwise this
        // onNext will break the contract.
        if (buffer != null) ack.syncOnContinue {
          out.onNext(buffer)
          buffer = null
          out.onComplete()
        } else {
          buffer = null
          out.onComplete()
        }
      }
    }
}