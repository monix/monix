/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import scala.util.control.NonFatal
import monix.reactive.Observable.Operator
import monix.reactive.internal.operators.CollectOperator.{ checkFallback, isDefined }
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final class CollectWhileOperator[-A, +B](pf: PartialFunction[A, B]) extends Operator[A, B] {

  def apply(out: Subscriber[B]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var isActive = true

      def onNext(elem: A): Future[Ack] = {
        if (!isActive) Stop
        else {
          // Protects calls to user code from within an operator
          var streamError = true
          try {
            val next = pf.applyOrElse(elem, checkFallback[B])
            if (isDefined(next)) {
              streamError = false
              out.onNext(next)
            } else {
              isActive = false
              out.onComplete()
              Stop
            }
          } catch {
            case NonFatal(ex) if streamError =>
              onError(ex)
              Stop
          }
        }
      }

      def onComplete() =
        if (isActive) {
          isActive = false
          out.onComplete()
        }

      def onError(ex: Throwable) =
        if (isActive) {
          isActive = false
          out.onError(ex)
        }
    }
}

private object CollectWhileOperator extends (Any => Any) {
  /** In the case a partial function is not defined, return a magic fallback value. */
  def checkFallback[B]: Any => B = this.asInstanceOf[Any => B]

  /** Indicates whether the result is the magic fallback value. */
  def isDefined(result: Any): Boolean = result.asInstanceOf[AnyRef] ne this

  /** Always returns `this`, used as the magic fallback value. */
  override def apply(elem: Any): Any = this
}
