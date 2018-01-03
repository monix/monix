/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.collection.mutable.WrappedArray

private[reactive] final class BufferSlidingOperator[A](count: Int, skip: Int)
  extends Operator[A, Seq[A]] {

  require(count > 0, "count must be strictly positive")
  require(skip > 0, "skip must be strictly positive")

  def apply(out: Subscriber[Seq[A]]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var isDone = false
      private[this] var ack: Future[Ack] = _

      private[this] val toDrop = if (count > skip) 0 else skip - count
      private[this] val toRepeat = if (skip > count) 0 else count - skip

      private[this] var buffer = new Array[AnyRef](count)
      private[this] var dropped = 0
      private[this] var length = 0

      @inline
      private def toSeq(array: Array[AnyRef]): Seq[A] =
        new WrappedArray.ofRef(array).asInstanceOf[Seq[A]]

      def onNext(elem: A): Future[Ack] = {
        if (isDone)
          Stop
        else if (dropped > 0) {
          dropped -= 1
          Continue
        }
        else {
          buffer(length) = elem.asInstanceOf[AnyRef]
          length += 1

          if (length < count) Continue else {
            val oldBuffer = buffer
            buffer = new Array(count)

            if (toRepeat > 0) {
              System.arraycopy(oldBuffer, count-toRepeat, buffer, 0, toRepeat)
              length = toRepeat
            } else {
              dropped = toDrop
              length = 0
            }

            // signaling downstream
            ack = out.onNext(toSeq(oldBuffer))
            ack
          }
        }
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          buffer = null // GC purposes
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true

          val threshold = if (ack == null) 0 else toRepeat
          if (length > threshold) {
            if (ack == null) ack = Continue
            ack.syncOnContinue {
              out.onNext(toSeq(buffer.take(length)))
              out.onComplete()
              buffer = null // GC purposes
            }
          }
          else
            out.onComplete()
        }
    }
}
