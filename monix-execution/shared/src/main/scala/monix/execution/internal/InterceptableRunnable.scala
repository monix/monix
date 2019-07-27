/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

/**
  * A `Runnable` which can be wrapped to report exceptions raised using another
  * [[UncaughtExceptionReporter]]
  */
trait InterceptableRunnable extends Runnable {
  def intercept(handler: UncaughtExceptionReporter): Runnable
}

object InterceptableRunnable {
  private[this] class Wrapped(r: Runnable, handler: UncaughtExceptionReporter) extends InterceptableRunnable {
    def run(): Unit =
      try {
        r.run()
      } catch { case NonFatal(ex) => handler.reportFailure(ex) }

    // can't reinstall a handler on top
    def intercept(handler: UncaughtExceptionReporter): Runnable = this
  }

  private[this] class Delegate(r: Runnable) extends InterceptableRunnable {
    def run(): Unit = r.run()
    def intercept(handler: UncaughtExceptionReporter): Runnable =
      if (r.isInstanceOf[TrampolinedRunnable]) new Wrapped(r, handler) with TrampolinedRunnable
      else new Wrapped(r, handler)
  }

  def apply(r: Runnable): InterceptableRunnable = r match {
    case ir: InterceptableRunnable => ir
    case _: TrampolinedRunnable => new Delegate(r) with TrampolinedRunnable
    case _ => new Delegate(r)
  }

  def apply(r: Runnable, handler: UncaughtExceptionReporter): Runnable =
    if (handler eq null) r else apply(r).intercept(handler)
}
