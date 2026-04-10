/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution.internal

import scala.util.control.NonFatal
import monix.execution.UncaughtExceptionReporter
import monix.execution.schedulers.TrampolinedRunnable

private[execution] class InterceptRunnable(r: Runnable, handler: UncaughtExceptionReporter) extends Runnable {

  override final def run(): Unit =
    try r.run()
    catch { case NonFatal(e) => handler.reportFailure(e) }
}

private[execution] object InterceptRunnable {
  def apply(r: Runnable, h: UncaughtExceptionReporter): Runnable = r match {
    case ir: InterceptRunnable => ir
    case _: TrampolinedRunnable => new InterceptRunnable(r, h) with TrampolinedRunnable
    case _ if h != null => new InterceptRunnable(r, h)
    case _ => r
  }
}
