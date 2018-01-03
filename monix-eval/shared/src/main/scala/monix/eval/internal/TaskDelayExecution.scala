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

private[eval] object TaskDelayExecution {
  /**
    * Implementation for `Task.delayExecution`
    */
  def apply[A](self: Task[A], timespan: FiniteDuration): Task[A] =
    Task.unsafeCreate { (context, cb) =>
      implicit val s = context.scheduler
      val conn = context.connection
      val c = SingleAssignmentCancelable()
      conn push c

      c := s.scheduleOnce(timespan.length, timespan.unit, new Runnable {
        def run(): Unit = {
          conn.pop()
          // We had an async boundary, as we must reset the frame
          context.frameRef.reset()
          Task.unsafeStartNow(self, context, Callback.async(cb))
        }
      })
    }
}
