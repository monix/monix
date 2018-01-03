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
import monix.execution.atomic.Atomic
import monix.execution.cancelables.AssignableCancelable
import monix.execution.misc.NonFatal
import monix.reactive.{Consumer, Observer}
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/** Implementation for [[monix.reactive.Consumer.fromObserver]]. */
private[reactive]
final class FromObserverConsumer[In](f: Scheduler => Observer[In])
  extends Consumer[In, Unit] {

  def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
    Try(f(s)) match {
      case Failure(ex) =>
        Consumer.raiseError(ex).createSubscriber(cb,s)

      case Success(out) =>
        val sub = new Subscriber[In] { self =>
          implicit val scheduler = s

          private[this] val isDone = Atomic(false)
          private def signal(ex: Throwable): Unit =
            if (!isDone.getAndSet(true)) {
              if (ex == null) {
                try out.onComplete()
                finally cb.onSuccess(())
              }
              else {
                try out.onError(ex)
                finally cb.onError(ex)
              }
            }

          def onNext(elem: In): Future[Ack] = {
            val ack = try out.onNext(elem) catch {
              case NonFatal(ex) => Future.failed(ex)
            }

            ack.syncOnComplete {
              case Success(result) =>
                if (result == Stop) signal(null)
              case Failure(ex) =>
                signal(ex)
            }

            ack
          }

          def onComplete(): Unit = signal(null)
          def onError(ex: Throwable): Unit = signal(ex)
        }

        (sub, AssignableCancelable.dummy)
    }
  }
}