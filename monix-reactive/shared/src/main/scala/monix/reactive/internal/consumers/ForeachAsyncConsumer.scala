/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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
import monix.eval.Task
import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Scheduler}
import monix.execution.cancelables.AssignableCancelable

import scala.util.control.NonFatal
import monix.reactive.Consumer
import monix.reactive.internal.util.TaskRun
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

/** Implementation for [[monix.reactive.Consumer.foreachTask]]. */
private[reactive]
final class ForeachAsyncConsumer[A](f: A => Task[Unit])
  extends Consumer[A, Unit] {

  def createSubscriber(cb: Callback[Throwable, Unit], s: Scheduler): (Subscriber[A], AssignableCancelable) = {
    val out = new Subscriber[A] {
      implicit val scheduler = s
      private[this] implicit val opts = TaskRun.options(scheduler)
      private[this] var isDone = false

      def onNext(elem: A): Future[Ack] = {
        try {
          f(elem).map(_ => Continue)
            .runToFutureOpt
            .syncTryFlatten
        } catch {
          case ex if NonFatal(ex) =>
            onError(ex)
            Stop
        }
      }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          cb.onSuccess(())
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