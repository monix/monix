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
import monix.execution.cancelables.SingleAssignmentCancelable
import scala.concurrent.duration.FiniteDuration

private[eval] object TaskDelayResult {
  /**
    * Implementation for `Task.delayResult`
    */
  def apply[A](self: Task[A], timespan: FiniteDuration): Task[A] =
    Task.unsafeCreate { (context, cb) =>
      implicit val s = context.scheduler
      val conn = context.connection

      Task.unsafeStartAsync(self, context,
        new Callback[A] {
          def onSuccess(value: A): Unit = {
            val task = SingleAssignmentCancelable()
            conn push task

            // Delaying result
            task := s.scheduleOnce(timespan.length, timespan.unit,
              new Runnable {
                def run(): Unit = {
                  conn.pop()
                  context.frameRef.reset()
                  cb.asyncOnSuccess(value)
                }
              })
          }

          def onError(ex: Throwable): Unit =
            cb.asyncOnError(ex)
        })
    }
}
