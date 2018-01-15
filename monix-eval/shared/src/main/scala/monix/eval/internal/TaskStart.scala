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

import monix.execution.schedulers.TrampolinedRunnable
import scala.concurrent.Promise

private[eval] object TaskStart {
  /**
    * Implementation for `Task.start`.
    */
  def apply[A](fa: Task[A]): Task[Task[A]] =
    fa match {
      // There's no point in evaluating strict stuff
      case Task.Now(_) | Task.Error(_) => Task.Now(fa)
      case _ =>
        Task.Async { (ctx, cb) =>
          // Light async boundary to prevent stack overflows
          ctx.scheduler.execute(new TrampolinedRunnable {
            def run(): Unit = {
              implicit val sc = ctx.scheduler
              // Standard Scala promise gets used for storing or waiting
              // for the final result
              val p = Promise[A]()
              // Building the Task to signal, linked to the above Promise.
              // It needs its own context, its own cancelable
              val ctx2 = Task.Context(ctx.scheduler, ctx.options)
              val task = TaskFromFuture.lightBuild(p.future, ctx2.connection)
              // Starting actual execution of our newly created task;
              // This might block the current thread until completion or
              // first async boundary is hit, whichever comes first
              Task.unsafeStartNow(fa, ctx2, Callback.fromPromise(p))
              // Signal the created Task reference
              cb.onSuccess(task)
            }
          })
        }
    }
}
