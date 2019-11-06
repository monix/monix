/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import scala.concurrent.Promise

import monix.eval.Task.{Async, Context}
import monix.execution.misc.Local
import monix.execution.{Callback, CancelablePromise}

private[eval] object TaskStart {
  /**
    * Implementation for `Task.fork`.
    */
  def forked[A](fa: Task[A]): Task[Fiber[A]] =
    fa match {
      // There's no point in evaluating strict stuff
      case Task.Now(_) | Task.Error(_) =>
        Task.Now(Fiber(fa, Task.unit))
      case _ =>
        Async(
          new StartForked(fa),
          trampolineBefore = false,
          trampolineAfter = true,
          restoreLocals = false
        )
    }

  private class StartForked[A](fa: Task[A]) extends ((Context, Callback[Throwable, Fiber[A]]) => Unit) {

    final def apply(ctx: Context, cb: Callback[Throwable, Fiber[A]]): Unit = {
      implicit val sc = ctx.scheduler
      // Cancelable Promise gets used for storing or waiting
      // for the final result
      val p = CancelablePromise[A]()
      // Building the Task to signal, linked to the above Promise.
      // It needs its own context, its own cancelable
      val ctx2 = Task.Context(ctx.scheduler, ctx.options)
      // Starting actual execution of our newly created task;
      val (fa2, pCtx) =
        if (ctx.options.contextIsolationOnFork) {
          val pCtx = Promise[Local.Context]()
          TaskLocal.isolate(fa.map(a => { pCtx.success(Local.getContext()); a })) -> pCtx
        } else {
          fa -> null
        }
      Task.unsafeStartEnsureAsync(fa2, ctx2, Callback.fromPromise(p))
      // Signal the created fiber
      val task = Task.fromCancelablePromise(p)
      val join =
        if (pCtx eq null) task
        else
          task.flatMap { a =>
            if (pCtx ne null)
              Task
                .fromFuture(pCtx.future)
                .flatMap(ctx =>
                  Task {
                    Local.setContext(ctx)
                    a
                  })
            else Task.pure(a)
          }

      cb.onSuccess(Fiber(join, ctx2.connection.cancel))
    }
  }
}
