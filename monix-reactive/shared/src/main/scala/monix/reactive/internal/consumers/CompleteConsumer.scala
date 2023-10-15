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

package monix.reactive.internal.consumers

import monix.execution.Callback
import monix.execution.Ack.Continue
import monix.execution.{ Ack, Scheduler }
import monix.execution.cancelables.AssignableCancelable
import monix.reactive.Consumer
import monix.reactive.observers.Subscriber

/** Implementation for [[monix.reactive.Consumer.complete]]. */
private[reactive] object CompleteConsumer extends Consumer.Sync[Any, Unit] {
  override def createSubscriber(
    cb: Callback[Throwable, Unit],
    s: Scheduler
  ): (Subscriber.Sync[Any], AssignableCancelable) = {
    val out = new Subscriber.Sync[Any] {
      implicit val scheduler: Scheduler = s
      def onNext(elem: Any): Ack = Continue
      def onComplete(): Unit = cb.onSuccess(())
      def onError(ex: Throwable): Unit = cb.onError(ex)
    }

    (out, AssignableCancelable.dummy)
  }
}
