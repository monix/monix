/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import monix.execution.cancelables.{AssignableCancelable, MultiAssignmentCancelable, SingleAssignmentCancelable}
import monix.execution.schedulers.TrampolinedRunnable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observables.ChainedObservable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] final
class ExecuteOnObservable[+A](source: Observable[A], s: Scheduler, forceAsync: Boolean)
  extends ChainedObservable[A] {

  def unsafeSubscribeFn(conn: MultiAssignmentCancelable, out: Subscriber[A]): Unit = {
    executeAsync(conn, out, source.isInstanceOf[ChainedObservable[_]])
  }

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val isChained = source.isInstanceOf[ChainedObservable[_]]
    val conn =
      if (isChained) MultiAssignmentCancelable()
      else SingleAssignmentCancelable()

    executeAsync(conn, out, isChained)
    conn
  }

  private def executeAsync(conn: AssignableCancelable, out: Subscriber[A], isChained: Boolean): Unit = {
    if (forceAsync) s.execute(new Thunk(conn, out, isChained))
    else s.execute(new TrampolinedThunk(conn, out, isChained))
  }

  private final class TrampolinedThunk
    (conn: AssignableCancelable, out: Subscriber[A], isChained: Boolean)
    extends Thunk(conn, out, isChained) with TrampolinedRunnable

  private class Thunk(conn: AssignableCancelable, out: Subscriber[A], isChained: Boolean)
    extends Runnable {

    final def run(): Unit = {
      val out2 = new Subscriber[A] {
        val scheduler: Scheduler = s
        def onError(ex: Throwable): Unit =
          out.onError(ex)
        def onComplete(): Unit =
          out.onComplete()
        def onNext(elem: A): Future[Ack] =
          out.onNext(elem)
      }

      if (isChained) {
        source.asInstanceOf[ChainedObservable[A]].unsafeSubscribeFn(
          conn.asInstanceOf[MultiAssignmentCancelable],
          out2)
      } else {
        conn := source.unsafeSubscribeFn(out2)
      }
    }
  }
}
