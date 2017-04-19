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
import monix.execution.misc.NonFatal

private[monix] object TaskDelayResultBySelector {
  /**
    * Implementation for `Task.delayResultBySelector`
    */
  def apply[A,B](self: Task[A], selector: A => Task[B]): Task[A] =
    Task.unsafeCreate { (context, cb) =>
      implicit val s = context.scheduler

      Task.unsafeStartAsync(self, context,
        new Callback[A] {
          def onSuccess(value: A): Unit = {
            var streamErrors = true
            try {
              val trigger = selector(value)
              streamErrors = false

              // Delaying result
              Task.unsafeStartAsync(trigger, context,
                new Callback[B] {
                  def onSuccess(b: B): Unit =
                    cb.asyncOnSuccess(value)
                  def onError(ex: Throwable): Unit =
                    cb.asyncOnError(ex)
                })
            } catch {
              case NonFatal(ex) if streamErrors =>
                cb.asyncOnError(ex)
            }
          }

          def onError(ex: Throwable): Unit =
            cb.asyncOnError(ex)
        })
    }
}
