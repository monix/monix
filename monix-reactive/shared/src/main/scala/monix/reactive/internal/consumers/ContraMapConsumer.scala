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
import monix.execution.misc.NonFatal
import monix.reactive.Consumer
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

/** Implementation for [[monix.reactive.Consumer.contramap]]. */
private[reactive]
final class ContraMapConsumer[In2, -In, +R](source: Consumer[In, R], f: In2 => In)
  extends Consumer[In2, R] {

  def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[In2], AssignableCancelable) = {
    val (out, c) = source.createSubscriber(cb, s)

    val out2 = new Subscriber[In2] {
      implicit val scheduler = out.scheduler
      // For protecting the contract
      private[this] var isDone = false

      def onError(ex: Throwable): Unit =
        if (!isDone) { isDone = true; out.onError(ex) }
      def onComplete(): Unit =
        if (!isDone) { isDone = true; out.onComplete() }

      def onNext(elem2: In2): Future[Ack] = {
        // Protects calls to user code from within the operator and
        // stream the error downstream if it happens, but if the
        // error happens because of calls to `onNext` or other
        // protocol calls, then the behavior should be undefined.
        var streamErrors = true
        try {
          val elem = f(elem2)
          streamErrors = false
          out.onNext(elem)
        } catch {
          case NonFatal(ex) if streamErrors =>
            onError(ex)
            Stop
        }
      }
    }

    (out2, c)
  }
}
