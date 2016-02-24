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

package monix.streams.internal.operators2

import monix.execution.Ack.{Cancel, Continue}
import monix.execution.cancelables.{CompositeCancelable, SingleAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.streams.observers.{Subscriber, SyncSubscriber}
import monix.streams.{CanObserve, Observable}
import scala.concurrent.Future
import scala.language.higherKinds

private[streams] final class DropUntilObservable[A, F[_] : CanObserve]
  (source: Observable[A], trigger: F[_])
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = SingleAssignmentCancelable()
    val composite = CompositeCancelable(task)

    composite += source.unsafeSubscribeFn(    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var isActive = true
      private[this] var errorThrown: Throwable = null
      @volatile private[this] var shouldDrop = true

      private[this] def interruptDropMode(ex: Throwable = null): Cancel = {
        // must happen before changing shouldDrop
        errorThrown = ex
        shouldDrop = false
        Cancel
      }

      task := CanObserve[F].observable(trigger).unsafeSubscribeFn(
        new SyncSubscriber[Any] {
          implicit val scheduler = out.scheduler
          def onNext(elem: Any) = interruptDropMode(null)
          def onComplete(): Unit = interruptDropMode(null)
          def onError(ex: Throwable): Unit = interruptDropMode(ex)
        })

      def onNext(elem: A): Future[Ack] = {
        if (!isActive)
          Cancel
        else if (shouldDrop)
          Continue
        else if (errorThrown != null) {
          onError(errorThrown)
          Cancel
        } else {
          out.onNext(elem)
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