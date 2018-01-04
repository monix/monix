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

private[reactive] object IsEmptyOperator extends Operator[Any,Boolean] {
  def apply(out: Subscriber[Boolean]): Subscriber[Any] =
    new Subscriber[Any] {
      implicit val scheduler = out.scheduler
      private[this] var isDone = false
      private[this] var isEmpty = true

      def onNext(elem: Any): Ack = {
        isEmpty = false
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
          out.onNext(isEmpty)
          out.onComplete()
        }
    }
}