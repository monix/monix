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
class SumOperator[A : Numeric] extends Operator[A,A] {
  def apply(out: Subscriber[A]): Subscriber.Sync[A] =
    new Subscriber.Sync[A] {
      implicit val scheduler = out.scheduler
      private[this] var isDone = false
      private[this] val ev = implicitly[Numeric[A]]
      private[this] var isStarted = false
      private[this] var result: A = _

      def onNext(elem: A): Ack =
        try {
          if (!isStarted) {
            result = elem
            isStarted = true
          } else {
            result = ev.plus(result, elem)
          }

          Continue
        } catch {
          case NonFatal(ex) =>
            onError(ex)
            Stop
        }

      def onError(ex: Throwable) =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true

          // Protects calls to user code from within the operator and
          // stream the error downstream if it happens, but if the
          // error happens because of calls to `onNext` or other
          // protocol calls, then the behavior should be undefined.
          var streamError = true
          try {
            val r = if (isStarted) result else ev.zero
            streamError = false
            out.onNext(r)
            out.onComplete()
          } catch {
            case NonFatal(ex) if streamError =>
              out.onError(ex)
          }
        }
    }
}