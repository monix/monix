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
import monix.execution.Ack.Stop
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber

private[reactive] final class TakeLeftOperator[A](n: Long)
  extends Operator[A, A] {

  def apply(out: Subscriber[A]): Subscriber[A] = {
    if (n <= 0) zero(out) else positive(out)
  }

  private def zero(out: Subscriber[A]): Subscriber.Sync[A] =
    new Subscriber.Sync[A] {
      implicit val scheduler = out.scheduler
      private[this] var isDone = false

      def onNext(elem: A): Ack = {
        onComplete()
        Stop
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          out.onComplete()
        }
    }

  private def positive(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var counter = 0L
      private[this] var isActive = true

      def onNext(elem: A) = {
        if (isActive && counter < n) {
          // ^^ short-circuit for not endlessly incrementing that number
          counter += 1

          if (counter < n) {
            // this is not the last event in the stream, so send it directly
            out.onNext(elem)
          } else  {
            // last event in the stream, so we need to send the event followed by an EOF downstream
            // after which we signal upstream to the producer that it should stop
            isActive = false

            out.onNext(elem)
            out.onComplete()
            Stop
          }
        } else {
          // we already emitted the maximum number of events, so signal upstream
          // to the producer that it should stop sending events
          Stop
        }
      }

      def onError(ex: Throwable) =
        if (isActive) {
          isActive = false
          out.onError(ex)
        }

      def onComplete() =
        if (isActive) {
          isActive = false
          out.onComplete()
        }
    }
}