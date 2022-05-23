/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import monix.execution.{ Ack, Callback, Cancelable, Scheduler }
import monix.eval.Task
import monix.execution.Ack.{ Continue, Stop }
import monix.execution.cancelables.{ AssignableCancelable, SingleAssignCancelable }
import scala.util.control.NonFatal

import monix.reactive.Consumer
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

/** Implementation for [[monix.reactive.Consumer.foreachTask]]. */
private[reactive] final class ForeachAsyncConsumer[A](f: A => Task[Unit]) extends Consumer[A, Unit] {

  def createSubscriber(cb: Callback[Throwable, Unit], s: Scheduler): (Subscriber[A], AssignableCancelable) = {
    var isDone = false
    var lastCancelable = Cancelable.empty

    val out = new Subscriber[A] {
      implicit val scheduler = s

      def onNext(elem: A): Future[Ack] = {
        try {
          this.synchronized {
            if (!isDone) {
              val future = f(elem).redeem({ e => onError(e); Stop }, _ => Continue).runToFuture
              lastCancelable = future
              future.syncTryFlatten
            } else Stop
          }
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

    (
      out,
      SingleAssignCancelable.plusOne(Cancelable { () =>
        out.synchronized {
          isDone = true
          lastCancelable.cancel()
          lastCancelable = Cancelable.empty
        }
      })
    )
  }
}
