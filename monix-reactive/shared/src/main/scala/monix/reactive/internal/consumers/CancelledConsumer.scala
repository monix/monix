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

/** Implementation for [[monix.reactive.Consumer.cancel]]. */
private[reactive]
object CancelledConsumer extends Consumer.Sync[Any, Unit] {
  def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber.Sync[Any], AssignableCancelable) = {
    val out = new Subscriber.Sync[Any] {
      implicit val scheduler = s
      def onNext(elem: Any): Ack = Stop
      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)
    }

    // Forcing async boundary to prevent problems
    s.execute(new Runnable { def run() = cb.onSuccess(()) })
    (out, AssignableCancelable.alreadyCanceled)
  }
}
