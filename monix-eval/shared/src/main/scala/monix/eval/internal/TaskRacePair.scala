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

package monix.eval
package internal

import monix.execution.Cancelable
import monix.execution.atomic.Atomic
import monix.execution.cancelables.StackedCancelable
import scala.concurrent.Promise

private[eval] object TaskRacePair {
  // Type aliasing the result only b/c it's a mouthful
  type RaceEither[A, B] = Either[(A, Fiber[B]), (Fiber[A], B)]

  /**
    * Implementation for `Task.racePair`.
    */
  def apply[A, B](fa: Task[A], fb: Task[B]): Task[RaceEither[A, B]] =
    Task.Async(new Register(fa, fb), trampolineBefore = true, trampolineAfter = true)

  // Implementing Async's "start" via `ForkedStart` in order to signal
  // that this is a task that forks on evaluation.
  //
  // N.B. the contract is that the injected callback gets called after
  // a full async boundary!
  private final class Register[A, B](fa: Task[A], fb: Task[B])
    extends ForkedRegister[RaceEither[A, B]] {

    def apply(context: Task.Context, cb: Callback[RaceEither[A, B]]): Unit = {
      implicit val s = context.scheduler
      val conn = context.connection

      val pa = Promise[A]()
      val pb = Promise[B]()

      val isActive = Atomic(true)
      val connA = StackedCancelable()
      val connB = StackedCancelable()
      conn.push(Cancelable.trampolined(connA, connB))

      val contextA = context.withConnection(connA)
      val contextB = context.withConnection(connB)

      // First task: A
      Task.unsafeStartEnsureAsync(fa, contextA, new Callback[A] {
        def onSuccess(valueA: A): Unit =
          if (isActive.getAndSet(false)) {
            val fiberB = Fiber(TaskFromFuture.strict(pb.future), Task(connB.cancel()))
            conn.pop()
            cb.onSuccess(Left((valueA, fiberB)))
          } else {
            pa.success(valueA)
          }

        def onError(ex: Throwable): Unit =
          if (isActive.getAndSet(false)) {
            conn.pop()
            connB.cancel()
            cb.onError(ex)
          } else {
            pa.failure(ex)
          }
      })

      // Second task: B
      Task.unsafeStartEnsureAsync(fb, contextB, new Callback[B] {
        def onSuccess(valueB: B): Unit =
          if (isActive.getAndSet(false)) {
            val fiberA = Fiber(TaskFromFuture.strict(pa.future), Task(connA.cancel()))
            conn.pop()
            cb.onSuccess(Right((fiberA, valueB)))
          } else {
            pb.success(valueB)
          }

        def onError(ex: Throwable): Unit =
          if (isActive.getAndSet(false)) {
            conn.pop()
            connA.cancel()
            cb.onError(ex)
          } else {
            pb.failure(ex)
          }
      })
    }
  }
}
