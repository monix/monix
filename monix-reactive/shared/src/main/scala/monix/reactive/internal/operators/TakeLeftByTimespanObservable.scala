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
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[reactive] final
class TakeLeftByTimespanObservable[A](source: Observable[A], timespan: FiniteDuration)
  extends Observable[A] {


  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val composite = CompositeCancelable()

    composite += source.unsafeSubscribeFn(new Subscriber[A] with Runnable {
      implicit val scheduler = out.scheduler

      private[this] var isActive = true
      // triggers completion
      private[this] val task: Cancelable = {
        val ref = scheduler.scheduleOnce(timespan.length, timespan.unit, this)
        composite += ref
        ref
      }

      def run(): Unit = onComplete()
      private def deactivate(): Unit = synchronized {
        isActive = false
        task.cancel()
      }

      def onNext(elem: A): Future[Ack] = synchronized {
        if (isActive)
          out.onNext(elem).syncOnStopOrFailure(_ => deactivate())
        else {
          onComplete()
          Stop
        }
      }

      def onError(ex: Throwable): Unit = synchronized {
        if (isActive) {
          deactivate()
          out.onError(ex)
        }
      }

      def onComplete(): Unit = synchronized {
        if (isActive) {
          deactivate()
          out.onComplete()
        }
      }
    })
  }
}