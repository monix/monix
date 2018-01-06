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

import monix.execution.cancelables.MultiAssignmentCancelable
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] final
class SwitchIfEmptyObservable[+A](source: Observable[A], backup: Observable[A])
  extends Observable[A] {

  override def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val cancelable = MultiAssignmentCancelable()

    val mainSub = source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var isEmpty = true

      def onNext(elem: A): Future[Ack] = {
        if (isEmpty) isEmpty = false
        out.onNext(elem)
      }

      def onComplete(): Unit = {
        // If the source was empty, switch to the backup.
        if (isEmpty)
          cancelable.orderedUpdate(backup.unsafeSubscribeFn(out), 2)
        else
          out.onComplete()
      }

      def onError(ex: Throwable): Unit =
        out.onError(ex)
    })

    cancelable.orderedUpdate(mainSub, 1)
  }
}
