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

import monix.execution.cancelables.{AssignableCancelable, SingleAssignmentCancelable}
import monix.execution.schedulers.TrampolinedRunnable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] final
class ExecuteOnObservable[+A](source: Observable[A], s: Scheduler, forceAsync: Boolean)
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val conn = SingleAssignmentCancelable()
    if (forceAsync) s.execute(new Thunk(conn, out))
    else s.execute(new TrampolinedThunk(conn, out))
    conn
  }

  private final class TrampolinedThunk
    (conn: AssignableCancelable, out: Subscriber[A])
    extends Thunk(conn, out) with TrampolinedRunnable

  private class Thunk(conn: AssignableCancelable, out: Subscriber[A])
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

      conn := source.unsafeSubscribeFn(out2)
    }
  }
}
