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

package monix.streams.internal.operators2

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.streams.ObservableLike.Operator
import monix.streams.observers.Subscriber
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

private[streams] final class BufferOperator[A](count: Int, skip: Int)
  extends Operator[A, Seq[A]] {

  require(count > 0, "count must be strictly positive")
  require(skip > 0, "skip must be strictly positive")

  def apply(out: Subscriber[Seq[A]]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var ack: Future[Ack] = Continue
      private[this] val shouldDrop = skip > count
      private[this] var leftToDrop = 0
      private[this] val shouldOverlap = skip < count
      private[this] var nextBuffer = ArrayBuffer.empty[A]
      private[this] var buffer = null : ArrayBuffer[A]
      private[this] var size = 0

      def onNext(elem: A): Future[Ack] = {
        if (shouldDrop && leftToDrop > 0) {
          leftToDrop -= 1
          Continue
        } else {
          if (buffer == null) {
            buffer = nextBuffer
            size = nextBuffer.length
            nextBuffer = ArrayBuffer.empty[A]
          }

          size += 1
          buffer.append(elem)
          if (shouldOverlap && size - skip > 0) nextBuffer += elem

          if (size >= count) {
            if (shouldDrop) leftToDrop = skip - count
            ack = out.onNext(buffer)
            buffer = null
          } else {
            ack = Continue
          }

          ack
        }
      }

      def onError(ex: Throwable): Unit = {
        // Drops any pending items
        buffer = null
        nextBuffer = null
        out.onError(ex)
      }

      def onComplete(): Unit = {
        // In case the last onNext isn't finished, then
        // we need to apply back-pressure, otherwise this
        // onNext will break the contract.
        if (buffer != null) ack.syncOnContinue {
          out.onNext(buffer)
          buffer = null
          nextBuffer = null
          out.onComplete()
        } else {
          buffer = null
          nextBuffer = null
          out.onComplete()
        }
      }
    }
}