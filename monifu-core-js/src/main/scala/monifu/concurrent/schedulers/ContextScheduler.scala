/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.concurrent.schedulers

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import monifu.concurrent.{Scheduler, Cancelable}
import scala.concurrent.ExecutionContext
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.cancelables.BooleanCancelable


private[concurrent] final class ContextScheduler(ec: ExecutionContext) extends Scheduler {
  override def scheduleOnce(action: => Unit): Cancelable = {
    val cancelable = BooleanCancelable()
    execute(new Runnable {
      def run() = if (!cancelable.isCanceled) action
    })
    cancelable
  }

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
    val sub = SingleAssignmentCancelable()
    val task = setTimeout(initialDelay.toMillis, () => {
      if (!sub.isCanceled)
        execute(new Runnable {
          def run() = if (!sub.isCanceled) action
        })
    })

    sub := Cancelable(clearTimeout(task))
    sub
  }

  def execute(runnable: Runnable): Unit =
    ec.execute(runnable)

  def reportFailure(t: Throwable): Unit =
    ec.reportFailure(t)

  private[this] def setTimeout(delayMillis: Long, cb: () => Unit): js.Dynamic = {
    val lambda: js.Function = () =>
      try { cb } catch { case t: Throwable => reportFailure(t) }
    js.Dynamic.global.setTimeout(lambda, delayMillis)
  }

  private[this] def clearTimeout(task: js.Dynamic) = {
    js.Dynamic.global.clearTimeout(task)
  }
}
