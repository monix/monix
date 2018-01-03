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

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.{CompositeCancelable, SingleAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] final class DropUntilObservable[A](source: Observable[A], trigger: Observable[Any])
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = SingleAssignmentCancelable()
    val composite = CompositeCancelable(task)

    composite += source.unsafeSubscribeFn(    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var isActive = true
      private[this] var errorThrown: Throwable = null
      @volatile private[this] var shouldDrop = true

      private[this] def interruptDropMode(ex: Throwable = null): Ack = {
        // must happen before changing shouldDrop
        errorThrown = ex
        shouldDrop = false
        Stop
      }

      locally {
        task := trigger.unsafeSubscribeFn(
          new Subscriber.Sync[Any] {
            implicit val scheduler = out.scheduler
            def onNext(elem: Any) = interruptDropMode(null)
            def onComplete(): Unit = interruptDropMode(null)
            def onError(ex: Throwable): Unit = interruptDropMode(ex)
          })
      }

      def onNext(elem: A): Future[Ack] = {
        if (!isActive)
          Stop
        else if (shouldDrop)
          Continue
        else if (errorThrown != null) {
          onError(errorThrown)
          Stop
        } else {
          out.onNext(elem).syncOnStopOrFailure(_ => task.cancel())
        }
      }

      def onError(ex: Throwable): Unit =
        if (isActive) {
          isActive = false
          try out.onError(ex) finally {
            task.cancel()
          }
        }

      def onComplete(): Unit =
        if (isActive) {
          isActive = false
          try out.onComplete() finally {
            task.cancel()
          }
        }
    })

    composite
  }
}