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

package monix.reactive.internal.operators

import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber
import scala.collection.immutable.Queue
import scala.concurrent.Future

private[reactive] final class BufferSlidingOperator[A](count: Int, skip: Int)
  extends Operator[A, Seq[A]] {

  require(count > 0, "count must be strictly positive")
  require(skip > 0, "skip must be strictly positive")

  def apply(out: Subscriber[Seq[A]]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var isDone = false
      private[this] var ack: Future[Ack] = Continue
      private[this] var buffer = Queue.empty[A]
      private[this] var size = 0
      private[this] var trigger = 0

      def onNext(elem: A): Future[Ack] = {
        if (isDone) Stop else {
          buffer = buffer.enqueue(elem)
          size += 1

          if (size < count) Continue else {
            if (trigger > 0) {
              trigger -= 1
              Continue
            }
            else {
              trigger = skip-1
              while (size > count) {
                val (_, state) = buffer.dequeue
                buffer = state
                size -= 1
              }

              // signaling downstream
              ack = out.onNext(buffer)
              ack
            }
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

          // Did we ever signal anything? If no, then we need
          // to send the whole buffer
          if (size > 0 && size < count) {
            out.onNext(buffer)
            out.onComplete()
            buffer = null // GC purposes
          }
          else {
            // Do we have items left to signal?
            val toSignal = count - (trigger + 1)
            // In case we have overlap, because of a skip < count,
            // then we shouldn't repeat old events unless we also
            // have new events to signal, hence this threshold
            val threshold = math.max(0, count - skip)

            if (toSignal > threshold && size > 0) {
              while (size > toSignal) {
                val (_, state) = buffer.dequeue
                buffer = state
                size -= 1
              }

              // Back-pressuring last onNext
              ack.syncOnContinue {
                out.onNext(buffer)
                out.onComplete()
                buffer = null // GC purposes
              }
            }
            else {
              // No extra elements to signal, no need for back-pressure
              out.onComplete()
            }
          }
        }
    }
}
