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

import monix.eval.Task.{Async, Context}
import monix.eval.internal.TaskCreate.CancelableCallback
import monix.eval.{Callback, Task}
import monix.execution.Cancelable

private[eval] object TaskDoOnCancel {
  /**
    * Implementation for `Task.doOnCancel`
    */
  def apply[A](self: Task[A], callback: Task[Unit]): Task[A] = {
    if (callback eq Task.unit) self else {
      val start = (context: Context, onFinish: Callback[A]) => {
        implicit val s = context.scheduler
        implicit val o = context.options

        val c = Cancelable(() => callback.runAsyncOpt(Callback.empty))
        val conn = context.connection
        conn.push(c)
        Task.unsafeStartNow(self, context, new CancelableCallback(conn, onFinish))
      }
      Async(start, restoreLocals = false)
    }
  }
}
