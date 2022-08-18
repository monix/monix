/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import cats.effect.CancelToken
import monix.eval.Task.{ Async, Context }
import monix.execution.{ Callback, Scheduler }
import monix.execution.atomic.{ Atomic, AtomicBoolean }
import monix.execution.schedulers.TrampolinedRunnable

private[eval] object TaskCancellation {
  /**
    * Implementation for `Task.uncancelable`.
    */
  def uncancelable[A](fa: Task[A]): Task[A] =
    Task.ContextSwitch(fa, withConnectionUncancelable, restoreConnection)

  /**
    * Implementation for `Task.onCancelRaiseError`.
    */
  def raiseError[A](fa: Task[A], e: Throwable): Task[A] = {
    val start = (ctx: Context, cb: Callback[Throwable, A]) => {
      implicit val sc = ctx.scheduler
      val canCall = Atomic(true)
      // We need a special connection because the main one will be reset on
      // cancellation and this can interfere with the cancellation of `fa`
      val connChild = TaskConnection()
      val conn = ctx.connection
      // Registering a special cancelable that will trigger error on cancel.
      // Note the pair `conn.pop` happens in `RaiseCallback`.
      conn.push(raiseCancelable(canCall, conn, connChild, cb, e))
      // Registering a callback that races against the cancelable we
      // registered above
      val cb2 = new RaiseCallback[A](canCall, conn, cb)
      Task.unsafeStartNow(fa, ctx, cb2)
    }
    Async(start, trampolineBefore = true, trampolineAfter = false, restoreLocals = false)
  }

  private final class RaiseCallback[A](
    waitsForResult: AtomicBoolean,
    conn: TaskConnection,
    cb: Callback[Throwable, A]
  )(implicit s: Scheduler)
    extends Callback[Throwable, A] with TrampolinedRunnable {

    private[this] var value: A = _
    private[this] var error: Throwable = _

    def run(): Unit = {
      val e = error
      if (e ne null) cb.onError(e)
      else cb.onSuccess(value)
    }

    def onSuccess(value: A): Unit =
      if (waitsForResult.getAndSet(false)) {
        conn.pop()
        this.value = value
        s.execute(this)
      }

    def onError(e: Throwable): Unit =
      if (waitsForResult.getAndSet(false)) {
        conn.pop()
        this.error = e
        s.execute(this)
      } else {
        s.reportFailure(e)
      }
  }

  private def raiseCancelable[A](
    waitsForResult: AtomicBoolean,
    conn: TaskConnection,
    conn2: TaskConnection,
    cb: Callback[Throwable, A],
    e: Throwable
  ): CancelToken[Task] = {

    Task.suspend {
      if (waitsForResult.getAndSet(false))
        conn2.cancel.map { _ =>
          conn.tryReactivate()
          cb.onError(e)
        }
      else
        Task.unit
    }
  }

  private[this] val withConnectionUncancelable: Context => Context =
    ct => {
      ct.withConnection(TaskConnection.uncancelable)
        .withOptions(ct.options.disableAutoCancelableRunLoops)
    }

  private[this] val restoreConnection: (Any, Throwable, Context, Context) => Context =
    (_, _, old, ct) => {
      ct.withConnection(old.connection)
        .withOptions(old.options)
    }
}
