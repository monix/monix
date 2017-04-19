/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

import monix.eval.{Callback, Coeval, Task}
import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber
import monix.reactive.internal.util.Instances._

import scala.concurrent.Future
import scala.util.Success

private[reactive] final
class EvalOnTerminateOperator[A](onTerminate: Option[Throwable] => Task[Unit], happensBefore: Boolean)
  extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      // Wrapping in a cancelable in order to protect it from
      // being called multiple times
      private[this] val active = Atomic(true)
      implicit val scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = {
        val result =
          try out.onNext(elem)
          catch { case NonFatal(ex) => Future.failed(ex) }

        val task = Task.fromFuture(result).materializeAttempt.flatMap {
          case Coeval.Now(ack) =>
            ack match {
              case Continue => ContinueTask
              case Stop =>
                onTerminate(None).map(_ => Stop)
            }
          case Coeval.Error(ex) =>
            onError(ex)
            onTerminate(Some(ex)).map(_ => Stop)
        }

        val errorCoverage = task.onErrorHandle { ex =>
          scheduler.reportFailure(ex)
          Stop
        }

        val future = errorCoverage.runAsync
        // Execution might be immediate
        future.value match {
          case Some(Success(ack)) => ack
          case _ => future
        }
      }

      private def onFinish(ex: Option[Throwable]): Task[Unit] = {
        @inline def triggerSignal(): Unit = ex match {
          case None => out.onComplete()
          case Some(ref) => out.onError(ref)
        }

        if (active.getAndSet(false)) {
          var streamErrors = true
          try if (happensBefore) {
            val task = onTerminate(ex).onErrorHandle { ex => scheduler.reportFailure(ex) }
            streamErrors = false
            task.map { _ => triggerSignal() }
          }
          else {
            streamErrors = false
            triggerSignal()
            onTerminate(ex)
          }
          catch { case NonFatal(err) =>
            if (streamErrors) {
              out.onError(err)
              ex.foreach(scheduler.reportFailure)
            } else {
              scheduler.reportFailure(err)
            }
            Task.unit
          }
        } else {
          ex.foreach(scheduler.reportFailure)
          Task.unit
        }
      }

      def onComplete(): Unit =
        onFinish(None).runAsync(Callback.empty)
      def onError(ex: Throwable): Unit =
        onFinish(Some(ex)).runAsync(Callback.empty)
    }
}