/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
import monix.execution.Cancelable

private[monix] object TaskDoOnCancel {
  /**
    * Implementation for `Task.doOnCancel`
    */
  def apply[A](self: Task[A], callback: Task[Unit]): Task[A] =
    Task.unsafeCreate { (context, onFinish) =>
      implicit val s = context.scheduler
      val c = Cancelable(() => callback.runAsync(Callback.empty))
      val conn = context.connection
      conn.push(c)

      // Light asynchronous boundary
      Task.unsafeStartTrampolined(self, context, new Callback[A] {
        def onSuccess(value: A): Unit = {
          conn.pop()
          onFinish.asyncOnSuccess(value)
        }

        def onError(ex: Throwable): Unit = {
          conn.pop()
          onFinish.asyncOnError(ex)
        }
      })
    }
}
