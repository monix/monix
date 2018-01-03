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

import monix.execution.Ack.Continue
import monix.execution.cancelables.{CompositeCancelable, SingleAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[reactive] final
class DropByTimespanObservable[A](source: Observable[A], timespan: FiniteDuration)
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val trigger = SingleAssignmentCancelable()
    val composite = CompositeCancelable(trigger)

    composite += source.unsafeSubscribeFn(
      new Subscriber[A] with Runnable { self =>
        implicit val scheduler = out.scheduler
        @volatile private[this] var shouldDrop = true

        locally {
          trigger := scheduler.scheduleOnce(timespan.length, timespan.unit, self)
        }

        def onNext(elem: A): Future[Ack] = {
          if (shouldDrop)
            Continue
          else
            out.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          trigger.cancel()
          out.onError(ex)
        }

        def onComplete(): Unit = {
          trigger.cancel()
          out.onComplete()
        }

        def run(): Unit = {
          shouldDrop = false
        }
      })

    composite
  }
}