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

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.cancelables.MultiAssignmentCancelable
import monix.reactive.Observable
import monix.reactive.observables.ChainedObservable
import monix.reactive.observables.ChainedObservable.{subscribe => chain}
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

/** Implementation for observable concatenation `++`. */
private[reactive] final class ConcatObservable[A](lh: Observable[A], rh: Observable[A])
  extends ChainedObservable[A] {

  def unsafeSubscribeFn(conn: MultiAssignmentCancelable, out: Subscriber[A]): Unit = {
    chain(lh, conn, new Subscriber[A] {
      private[this] var ack: Future[Ack] = Continue
      implicit val scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = {
        ack = out.onNext(elem)
        ack
      }

      def onError(ex: Throwable): Unit =
        out.onError(ex)

      def onComplete(): Unit = {
        // This can create a stack issue, but `chainedSubscribe`
        // creates light async boundaries, so should be safe
        ack.syncOnContinue(chain(rh, conn, out))
      }
    })
  }
}

