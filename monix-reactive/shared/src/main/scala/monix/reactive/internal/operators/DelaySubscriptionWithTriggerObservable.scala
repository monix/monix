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
import monix.execution.cancelables.MultiAssignmentCancelable
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] final
class DelaySubscriptionWithTriggerObservable[A](source: Observable[A], trigger: Observable[Any])
  extends Observable[A] {

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    val cancelable = MultiAssignmentCancelable()

    val main = trigger.unsafeSubscribeFn(
      new Subscriber[Any] {
        implicit val scheduler = subscriber.scheduler
        private[this] var isDone = false

        def onNext(elem: Any): Future[Ack] = {
          if (isDone) Stop else {
            isDone = true
            cancelable.orderedUpdate(source.unsafeSubscribeFn(subscriber), order=2)
            Stop
          }
        }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            subscriber.onError(ex)
          }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cancelable.orderedUpdate(source.unsafeSubscribeFn(subscriber), order=2)
          }
      })

    cancelable.orderedUpdate(main, order=1)
  }
}