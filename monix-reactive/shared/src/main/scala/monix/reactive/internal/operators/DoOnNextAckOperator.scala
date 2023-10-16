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

package monix.reactive.internal.operators

import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.Stop
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.util.control.NonFatal

private[reactive] final class DoOnNextAckOperator[A](cb: (A, Ack) => Task[Unit]) extends Operator[A, A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] { self =>
      implicit val scheduler: Scheduler = out.scheduler
      private[this] val isActive = Atomic(true)

      def onNext(elem: A): Future[Ack] = {
        // We are calling out.onNext directly, meaning that in onComplete/onError
        // we don't have to do anything special to ensure that the last `onNext`
        // has been sent (like we are doing in mapTask); we only need to apply
        // back-pressure for the following onNext events
        val f = out.onNext(elem)
        val task = Task.fromFuture(f).flatMap { ack =>
          val r =
            try cb(elem, ack)
            catch { case ex if NonFatal(ex) => Task.raiseError(ex) }
          r.map(_ => ack).onErrorHandle { ex =>
            onError(ex); Stop
          }
        }

        // Execution might be immediate
        task.runToFuture.syncTryFlatten
      }

      def onComplete(): Unit = {
        if (isActive.getAndSet(false))
          out.onComplete()
      }

      def onError(ex: Throwable): Unit = {
        if (isActive.getAndSet(false))
          out.onError(ex)
        else
          scheduler.reportFailure(ex)
      }
    }
}
