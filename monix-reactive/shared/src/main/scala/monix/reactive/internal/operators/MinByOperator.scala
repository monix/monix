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
import monix.execution.misc.NonFatal
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber

private[reactive] final
class MinByOperator[A,B : Ordering](f: A => B) extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber.Sync[A] =
    new Subscriber.Sync[A] {
      implicit val scheduler = out.scheduler

      private[this] val ev = implicitly[Ordering[B]]
      private[this] var isDone = false
      private[this] var minValue: A = _
      private[this] var minValueU: B = _
      private[this] var hasValue = false

      def onNext(elem: A): Ack = {
        try {
          if (!hasValue) {
            hasValue = true
            minValue = elem
            minValueU = f(elem)
          } else {
            val m = f(elem)
            if (ev.compare(m, minValueU) < 0) {
              minValue = elem
              minValueU = m
            }
          }

          Continue
        } catch {
          case NonFatal(ex) =>
            onError(ex)
            Stop
        }
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          if (!hasValue)
            out.onComplete()
          else {
            out.onNext(minValue)
            out.onComplete()
          }
        }
    }
}