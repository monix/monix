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

import monix.execution.cancelables.SerialCancelable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[reactive] final class DelayOnCompleteObservable[A]
  (source: Observable[A], delay: FiniteDuration)
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = SerialCancelable()

    val c = source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler: Scheduler = out.scheduler
      private[this] var isDone = false

      def onNext(elem: A): Future[Ack] = {
        val ack = out.onNext(elem)
        ack.syncOnStopOrFailure(_ => task.cancel())
        ack
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          val scheduled = scheduler.scheduleOnce(delay.length, delay.unit,
            new Runnable { def run(): Unit = out.onComplete() })

          task.orderedUpdate(scheduled, order = 2)
        }
    })

    task.orderedUpdate(c, order = 1)
  }
}
