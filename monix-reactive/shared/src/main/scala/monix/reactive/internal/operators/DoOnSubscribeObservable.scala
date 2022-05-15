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

package monix.reactive.internal.operators

import monix.execution.Callback
import monix.eval.Task
import monix.execution.Ack.Stop
import monix.execution.atomic.Atomic
import monix.execution.cancelables.{OrderedCancelable, SingleAssignCancelable, StackedCancelable}
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import monix.execution.{Ack, Cancelable, FutureUtils}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

private[reactive] object DoOnSubscribeObservable {
  // Implementation for doBeforeSubscribe
  final class Before[+A](source: Observable[A], task: Task[Unit]) extends Observable[A] {
    def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
      implicit val s = subscriber.scheduler
      val conn = OrderedCancelable()

      val c = task.runAsync(new Callback[Throwable, Unit] {
        def onSuccess(value: Unit): Unit = {
          val c = source.unsafeSubscribeFn(subscriber)
          conn.orderedUpdate(c, order = 2)
          ()
        }

        def onError(ex: Throwable): Unit =
          subscriber.onError(ex)
      })

      conn.orderedUpdate(c, order = 1)
      conn
    }
  }

  // Implementation for doAfterSubscribe
  final class After[+A](source: Observable[A], task: Task[Unit]) extends Observable[A] {
    def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
      implicit val scheduler = out.scheduler
      val p = Promise[Unit]()

      val cancelable = source.unsafeSubscribeFn(
        new Subscriber[A] {
          implicit val scheduler = out.scheduler
          private[this] val completeGuard = Atomic(true)
          private[this] var isActive = false

          def onNext(elem: A): Future[Ack] = {
            if (isActive) {
              // Fast path (1)
              out.onNext(elem)
            } else if (p.isCompleted) {
              // Fast path (2)
              isActive = true
              p.future.value.get match {
                case Failure(e) =>
                  finalSignal(e)
                  Stop
                case _ =>
                  out.onNext(elem)
              }
            } else {
              FutureUtils.transformWith[Unit, Ack](
                p.future,
                {
                  case Success(_) => out.onNext(elem)
                  case Failure(e) =>
                    finalSignal(e)
                    Stop
                })(immediate)
            }
          }

          def onError(ex: Throwable): Unit = finalSignal(ex)
          def onComplete(): Unit = finalSignal(null)

          private def finalSignal(e: Throwable): Unit = {
            if (completeGuard.getAndSet(false)) {
              if (e != null) out.onError(e)
              else out.onComplete()
            } else if (e != null) {
              scheduler.reportFailure(e)
            }
          }
        }
      )

      val ref = SingleAssignCancelable()
      val conn = StackedCancelable(List(ref, cancelable))

      ref := task.runAsync(new Callback[Throwable, Unit] {
        def onSuccess(value: Unit): Unit = {
          conn.pop()
          p.success(())
          ()
        }
        def onError(ex: Throwable): Unit = {
          conn.pop()
          p.failure(ex)
          ()
        }
      })
      conn
    }
  }
}
