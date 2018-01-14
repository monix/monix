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

private[reactive] final class TakeEveryNthOperator[A](n: Int)
  extends Operator[A, A] {

  require(n > 0, "n must be strictly positive")

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var index = n

      def onNext(elem: A): Future[Ack] = {
        index -= 1
        if (index != 0)
          Continue
        else {
          index = n
          out.onNext(elem)
        }
      }

      def onError(ex: Throwable) =
        out.onError(ex)
      def onComplete() =
        out.onComplete()
    }
}

