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

import monix.execution.Ack.{Continue, Stop}
import monix.reactive.observables.ObservableLike
import ObservableLike.Operator
import monix.execution.misc.NonFatal
import monix.reactive.observers.Subscriber

private[reactive] final
class DropByPredicateWithIndexOperator[A](p: (A, Int) => Boolean)
  extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var continueDropping = true
      private[this] var index = 0
      private[this] var isDone = false

      def onNext(elem: A) = {
        if (continueDropping) {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          var streamError = true
          try {
            val isStillInvalid = p(elem, index)
            streamError = false

            if (isStillInvalid) {
              index += 1
              Continue
            } else {
              continueDropping = false
              out.onNext(elem)
            }
          } catch {
            case NonFatal(ex) if streamError =>
              onError(ex)
              Stop
          }
        }
        else
          out.onNext(elem)
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
}