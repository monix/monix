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

package monix.reactive.internal.consumers

import monix.eval.Callback
import monix.execution.Ack.Stop
import monix.execution.{Ack, Scheduler}
import monix.execution.cancelables.AssignableCancelable
import monix.reactive.Consumer
import monix.reactive.observers.Subscriber

/** Implementation for [[monix.reactive.Consumer.head]]. */
private[reactive]
final class HeadConsumer[A] extends Consumer.Sync[A, A] {
  override def createSubscriber(cb: Callback[A], s: Scheduler): (Subscriber.Sync[A], AssignableCancelable) = {
    val out = new Subscriber.Sync[A] {
      implicit val scheduler = s
      private[this] var isDone = false

      def onNext(elem: A): Ack = {
        isDone = true
        cb.onSuccess(elem)
        Stop
      }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          cb.onError(new NoSuchElementException("head"))
        }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          cb.onError(ex)
        }
    }

    (out, AssignableCancelable.dummy)
  }
}
