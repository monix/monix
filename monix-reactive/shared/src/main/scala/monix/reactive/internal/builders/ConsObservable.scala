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

package monix.reactive.internal.builders

import monix.execution.Cancelable
import monix.execution.cancelables.{AssignableCancelable, MultiAssignCancelable, SingleAssignCancelable}
import monix.reactive.Observable
import monix.reactive.observables.ChainedObservable
import monix.reactive.observables.ChainedObservable.{subscribe => chain}
import monix.reactive.observers.Subscriber

private[reactive] final class ConsObservable[+A](head: A, tail: Observable[A])
  extends ChainedObservable[A] {

  def unsafeSubscribeFn(conn: AssignableCancelable.Multi, out: Subscriber[A]): Unit = {
    import out.{scheduler => s}
    out.onNext(head).syncOnContinue(chain(tail, conn, out))(s)
  }

  private def simpleSubscribe(conn: SingleAssignCancelable, out: Subscriber[A]): Unit = {
    import out.scheduler
    out.onNext(head).syncOnContinue {
      conn := tail.unsafeSubscribeFn(out)
    }
  }

  override def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    if (!tail.isInstanceOf[ChainedObservable[_]]) {
      val conn = SingleAssignCancelable()
      simpleSubscribe(conn, out)
      conn
    } else {
      val conn = MultiAssignCancelable()
      unsafeSubscribeFn(conn, out)
      conn
    }
  }
}
