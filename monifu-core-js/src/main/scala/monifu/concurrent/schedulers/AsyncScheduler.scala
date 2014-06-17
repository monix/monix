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
import monifu.concurrent.{Cancelable, Scheduler}

private[concurrent] object AsyncScheduler extends Scheduler {
  override def scheduleOnce(action: => Unit): Cancelable = {
    val task = setTimeout(action)
    Cancelable(clearTimeout(task))
  }

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
    val task = setTimeout(initialDelay.toMillis, action)
    Cancelable(clearTimeout(task))
  }

  def reportFailure(t: Throwable): Unit =
    Console.err.println("Failure in async execution: " + t)

  def execute(runnable: Runnable): Unit = {
    val lambda: js.Function = () =>
      try { runnable.run() } catch { case t: Throwable => reportFailure(t) }
    js.Dynamic.global.setTimeout(lambda, 0)
  }

  private[this] def setTimeout(cb: => Unit): js.Dynamic = {
    val lambda: js.Function = () =>
      try { cb } catch { case t: Throwable => reportFailure(t) }
    js.Dynamic.global.setTimeout(lambda, 0)
  }

  private[this] def setTimeout(delayMillis: Long, cb: => Unit): js.Dynamic = {
    val lambda: js.Function = () =>
      try { cb } catch { case t: Throwable => reportFailure(t) }
    js.Dynamic.global.setTimeout(lambda, delayMillis)
  }

  private[this] def clearTimeout(task: js.Dynamic) = {
    js.Dynamic.global.clearTimeout(task)
  }
}
