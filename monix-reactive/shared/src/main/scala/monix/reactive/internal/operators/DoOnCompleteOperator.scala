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
import monix.reactive.observables.ObservableLike
import ObservableLike.Operator
import monix.execution.misc.NonFatal
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final
class DoOnCompleteOperator[A](cb: () => Unit) extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = out.onNext(elem)
      def onError(ex: Throwable): Unit = out.onError(ex)

      def onComplete(): Unit = {
        // Protects calls to user code from within the operator and
        // stream the error downstream if it happens.
        var streamError = true
        try {
          cb()
          streamError = false
          out.onComplete()
        }
        catch {
          case NonFatal(ex) if streamError =>
            out.onError(ex)
        }
      }
    }
}