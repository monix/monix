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
import monix.execution.atomic.{Atomic, PaddingStrategy}
import monix.execution.cancelables.{CompositeCancelable, StackedCancelable}

private[monix] object TaskChooseFirstOfList {
  /**
    * Implementation for `Task.chooseFirstOfList`
    */
  def apply[A](tasks: TraversableOnce[Task[A]]): Task[A] =
    Task.unsafeCreate { (scheduler, conn, frameRef, callback) =>
      implicit val s = scheduler
      val isActive = Atomic.withPadding(true, PaddingStrategy.LeftRight128)
      val composite = CompositeCancelable()
      conn.push(composite)

      val cursor = tasks.toIterator

      while (isActive.get && cursor.hasNext) {
        val task = cursor.next()
        val taskCancelable = StackedCancelable()
        composite += taskCancelable

        Task.unsafeStartAsync(task, scheduler, taskCancelable, frameRef, new Callback[A] {
          def onSuccess(value: A): Unit =
            if (isActive.getAndSet(false)) {
              composite -= taskCancelable
              composite.cancel()
              conn.popAndCollapse(taskCancelable)
              callback.asyncOnSuccess(value)
            }

          def onError(ex: Throwable): Unit =
            if (isActive.getAndSet(false)) {
              composite -= taskCancelable
              composite.cancel()
              conn.popAndCollapse(taskCancelable)
              callback.asyncOnError(ex)
            } else {
              scheduler.reportFailure(ex)
            }
        })
      }
    }
}
