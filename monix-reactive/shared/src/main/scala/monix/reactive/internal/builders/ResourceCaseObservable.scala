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

package monix.reactive
package internal
package builders

import cats.effect.ExitCase
import monix.execution.Callback
import monix.eval.Task
import monix.execution.Ack.{ Continue, Stop }
import monix.execution.cancelables.Cancelable
import monix.execution.cancelables.AssignableCancelable
import monix.reactive.observables.ChainedObservable
import monix.reactive.observers.Subscriber
import scala.util.Success

private[reactive] final class ResourceCaseObservable[A](
  acquire: Task[A],
  release: (A, ExitCase[Throwable]) => Task[Unit]
) extends ChainedObservable[A] {

  def unsafeSubscribeFn(conn: AssignableCancelable.Multi, subscriber: Subscriber[A]): Unit = {
    implicit val s = subscriber.scheduler

    acquire.runAsyncUncancelable(new Callback[Throwable, A] {
      def onSuccess(value: A): Unit = {
        conn := new StreamOne(value)
          .guaranteeCase(e => release(value, e))
          .unsafeSubscribeFn(subscriber)
        ()
      }

      def onError(ex: Throwable): Unit =
        subscriber.onError(ex)
    })
  }

  private final class StreamOne(value: A) extends Observable[A] {
    def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
      import monix.execution.schedulers.TrampolineExecutionContext.immediate

      out.onNext(value) match {
        case Continue =>
          out.onComplete()
        case Stop =>
          ()
        case async =>
          async.onComplete {
            case Success(Continue) => out.onComplete()
            case _ => ()
          }(immediate)
      }
      Cancelable.empty
    }
  }
}
