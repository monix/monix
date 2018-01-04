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
import monix.reactive.Notification
import monix.reactive.Notification.{OnComplete, OnError, OnNext}
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] final class MaterializeOperator[A]
  extends Operator[A,Notification[A]] {

  def apply(out: Subscriber[Notification[A]]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var isDone = false
      private[this] var ack: Future[Ack] = Continue

      def onNext(elem: A): Future[Ack] = {
        ack = out.onNext(OnNext(elem))
        ack
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          ack.syncOnContinue {
            out.onNext(OnError(ex))
            out.onComplete()
          }
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          ack.syncOnContinue {
            out.onNext(OnComplete)
            out.onComplete()
          }
        }
    }
}