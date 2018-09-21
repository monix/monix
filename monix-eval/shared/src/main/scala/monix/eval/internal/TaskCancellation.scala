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

import monix.eval.Task.{Async, Context}
import monix.execution.atomic.{Atomic, AtomicBoolean}
import monix.execution.cancelables.StackedCancelable
import monix.execution.schedulers.TrampolinedRunnable
import monix.execution.{Cancelable, Scheduler}

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
    val start = (ctx: Context, cb: Callback[A]) => {
      implicit val sc = ctx.scheduler
      val canCall = Atomic(true)
      // We need a special connection because the main one will be reset on
      // cancellation and this can interfere with the cancellation of `fa`
      val connChild = StackedCancelable()
      val conn = ctx.connection
      // Registering a special cancelable that will trigger error on cancel.
      // Note the pair `conn.pop` happens in `RaiseCallback`.
      conn.push(new RaiseCancelable(canCall, conn, connChild, cb, e))
      // Registering a callback that races against the cancelable we
      // registered above
      val cb2 = new RaiseCallback[A](canCall, conn, cb)
      Task.unsafeStartNow(fa, ctx, cb2)
    }
    Async(start, trampolineBefore = true, trampolineAfter = false, restoreLocals = false)
  }

  private final class RaiseCallback[A](
    waitsForResult: AtomicBoolean,
    conn: StackedCancelable,
    cb: Callback[A])
    (implicit s: Scheduler)
    extends Callback[A] with TrampolinedRunnable {

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

  private final class RaiseCancelable[A](
    waitsForResult: AtomicBoolean,
    conn: StackedCancelable,
    conn2: StackedCancelable,
    cb: Callback[A],
    e: Throwable)
    (implicit s: Scheduler)
    extends Cancelable with TrampolinedRunnable {

    def run(): Unit = {
      conn2.cancel()
      conn.tryReactivate()
      cb.onError(e)
    }

    override def cancel(): Unit =
      if (waitsForResult.getAndSet(false)) {
        s.execute(this)
      }
  }

  private[this] val withConnectionUncancelable: Context => Context =
    ct => {
      ct.withConnection(StackedCancelable.uncancelable)
        .withOptions(ct.options.disableAutoCancelableRunLoops)
    }

  private[this] val restoreConnection: (Any, Throwable, Context, Context) => Context =
    (_, _, old, ct) => {
      ct.withConnection(old.connection)
        .withOptions(old.options)
    }
}
