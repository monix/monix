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

private[eval] object TaskForkAndForget {
  /**
    * Implementation for `Task.startAndForget`.
    */
  def apply[A](fa: Task[A]): Task[Unit] =
    Task.Async[Unit] { (ctx, cb) =>
      implicit val sc = ctx.scheduler
      // It needs its own context, its own cancelable
      val ctx2 = Task.Context(sc, ctx.options)
      // Starting actual execution of our newly created task forcing new async boundary
      Task.unsafeStartAsync(fa, ctx2, Callback.empty)
      cb.asyncOnSuccess(())
    }
}