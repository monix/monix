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

package monix.eval.internal

import monix.eval.{Callback, Task}
import monix.execution.cancelables.{SingleAssignCancelable, StackedCancelable}
import monix.execution.misc.{Local, NonFatal}
import monix.execution.{Cancelable, Scheduler}

private[eval] object TaskCreate {
  /**
    * Implementation for `Task.create`
    */
  def apply[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] =
    Task.unsafeCreate { (ctx, cb) =>
      val s = ctx.scheduler
      val conn = ctx.connection
      val cancelable = SingleAssignCancelable()
      conn push cancelable

      try {
        val ref = register(s, new CreateCallback(conn, ctx.options, cb)(s))
        // Optimization to skip the assignment, as it's expensive
        if (!ref.isInstanceOf[Cancelable.IsDummy])
          cancelable := ref
      } catch {
        case ex if NonFatal(ex) =>
          // We cannot stream the error, because the callback might have
          // been called already and we'd be violating its contract,
          // hence the only thing possible is to log the error.
          s.reportFailure(ex)
      }
    }

  // Wraps a callback into an implementation that pops the stack
  // before calling onSuccess/onError, also restoring the `Local`
  // context in case the option is enabled
  private final class CreateCallback[A](
    conn: StackedCancelable,
    options: Task.Options,
    cb: Callback[A])
    (implicit s: Scheduler)
    extends Callback[A] {

    // Light protection against multiple calls — obviously it's not
    // synchronized and can only protect in cases where there's ordering guaranteed,
    // but fails to protect in race conditions. We don't care about those though.
    private[this] var awaitsResult = true

    // Capturing current context, in case the option is active, as we need
    // to restore it due to async boundaries triggered in `Async` that might
    // have invalidated our local context
    private[this] val locals =
      if (options.localContextPropagation) Local.getContext()
      else null

    def onSuccess(value: A): Unit =
      if (awaitsResult) {
        awaitsResult = false
        conn.pop()
        s.executeTrampolined { () =>
          // Restoring context if available, because un-traced
          // async boundaries might have happened
          if (locals ne null) Local.setContext(locals)
          cb.onSuccess(value)
        }
      }

    def onError(e: Throwable): Unit =
      if (awaitsResult) {
        awaitsResult = false
        conn.pop()
        s.executeTrampolined { () =>
          // Restoring context if available, because un-traced
          // async boundaries might have happened
          if (locals ne null) Local.setContext(locals)
          cb.onError(e)
        }
      } else {
        s.reportFailure(e)
      }
  }
}
