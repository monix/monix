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
import monix.execution.misc.NonFatal
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final
class DefaultIfEmptyOperator[A](default: () => A)
  extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var isEmpty = true

      def onNext(elem: A): Future[Ack] = {
        if (isEmpty) isEmpty = false
        out.onNext(elem)
      }

      def onError(ex: Throwable): Unit =
        out.onError(ex)

      def onComplete(): Unit = {
        if (!isEmpty) out.onComplete() else {
          var streamErrors = true
          try {
            val value = default()
            streamErrors = false
            out.onNext(value)
            out.onComplete()
          } catch {
            case NonFatal(ex) if streamErrors =>
              out.onError(ex)
          }
        }
      }
    }
}