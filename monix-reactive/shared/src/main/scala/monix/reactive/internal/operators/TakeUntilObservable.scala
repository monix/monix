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

package monix.reactive.internal.operators

import monix.execution.Ack.Stop
import monix.execution.cancelables.{CompositeCancelable, SingleAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

/** Implementation for [[monix.reactive.Observable.takeUntil]]. */
private[reactive] final class TakeUntilObservable[+A](
  source: Observable[A], trigger: Observable[Any])
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val mainConn = SingleAssignmentCancelable()
    var isComplete = false

    val selectorConn = trigger.unsafeSubscribeFn(
      new Subscriber.Sync[Any] {
        implicit val scheduler = out.scheduler

        def onNext(elem: Any): Ack = {
          signalComplete(null)
          Stop
        }

        def onComplete(): Unit = signalComplete(null)
        def onError(ex: Throwable): Unit = signalComplete(ex)

        private def signalComplete(ex: Throwable): Unit =
          mainConn.synchronized {
            if (!isComplete) {
              isComplete = true
              mainConn.cancel()
              if (ex == null) out.onComplete()
              else out.onError(ex)
            } else if  (ex != null) {
              scheduler.reportFailure(ex)
            }
          }
      })

    mainConn := source.unsafeSubscribeFn(
      new Subscriber[A] {
        implicit val scheduler = out.scheduler

        def onNext(elem: A): Future[Ack] =
          mainConn.synchronized {
            if (isComplete) Stop else
              out.onNext(elem).syncOnStopOrFailure { _ =>
                mainConn.synchronized {
                  isComplete = true
                  selectorConn.cancel()
                }
              }
          }

        def onError(ex: Throwable): Unit = signalComplete(ex)
        def onComplete(): Unit = signalComplete(null)

        def signalComplete(ex: Throwable): Unit =
          mainConn.synchronized {
            if (!isComplete) {
              isComplete = true
              selectorConn.cancel()
              if (ex == null) out.onComplete()
              else out.onError(ex)
            }
          }
      })

    CompositeCancelable(mainConn, selectorConn)
  }
}
