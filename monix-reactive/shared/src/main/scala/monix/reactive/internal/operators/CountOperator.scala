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
import monix.execution.Ack.Continue
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] object CountOperator extends Operator[Any,Long] {
  def apply(out: Subscriber[Long]): Subscriber[Any] =
    new Subscriber[Any] {
      implicit val scheduler = out.scheduler
      private[this] var count = 0L

      def onNext(elem: Any): Future[Ack] = {
        count += 1
        Continue
      }

      def onComplete() = {
        out.onNext(count)
        out.onComplete()
      }

      def onError(ex: Throwable) = {
        out.onError(ex)
      }
    }
}