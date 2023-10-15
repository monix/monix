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

import monix.execution.Callback
import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.{ Continue, Stop }
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import scala.util.control.NonFatal
import monix.reactive.internal.util.Instances._
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.util.Success

private[reactive] final class DoOnTerminateOperator[A](
  onTerminate: Option[Throwable] => Task[Unit],
  happensBefore: Boolean
) extends Operator[A, A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      // Wrapping in a cancelable in order to protect it from
      // being called multiple times
      private[this] val active = Atomic(true)
      implicit val scheduler: Scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = {
        val result =
          try out.onNext(elem)
          catch { case ex if NonFatal(ex) => Future.failed(ex) }

        val task = Task.fromFuture(result).attempt.flatMap {
          case Right(ack) =>
            ack match {
              case Continue => ContinueTask
              case Stop =>
                onTerminate(None).map(_ => Stop)
            }
          case Left(ex) =>
            onError(ex)
            onTerminate(Some(ex)).map(_ => Stop)
        }

        val errorCoverage = task.onErrorHandle { ex =>
          scheduler.reportFailure(ex)
          Stop
        }

        val future = errorCoverage.runToFuture
        // Execution might be immediate
        future.value match {
          case Some(Success(ack)) => ack
          case _ => future
        }
      }

      private def onFinish(ex: Option[Throwable]): Task[Unit] = {
        def triggerSignal(): Unit =
          ex match {
            case None => out.onComplete()
            case Some(ref) => out.onError(ref)
          }

        if (active.getAndSet(false)) {
          var streamErrors = true
          try if (happensBefore) {
              val task = onTerminate(ex).onErrorHandle { ex =>
                scheduler.reportFailure(ex)
              }
              streamErrors = false
              task.map { _ =>
                triggerSignal()
              }
            } else {
              streamErrors = false
              triggerSignal()
              onTerminate(ex)
            }
          catch {
            case err if NonFatal(err) =>
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

      def onComplete(): Unit = {
        onFinish(None).runAsync(Callback.empty)
        ()
      }

      def onError(ex: Throwable): Unit = {
        onFinish(Some(ex)).runAsync(Callback.empty)
        ()
      }
    }
}
