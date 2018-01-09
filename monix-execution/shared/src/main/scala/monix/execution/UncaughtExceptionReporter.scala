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

package monix.execution

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

/** An exception reporter is a function that logs an uncaught error.
  *
  * Usually taken as an implicit when executing computations that could fail,
  * but that must not blow up the call-stack, like asynchronous tasks.
  *
  * A default implicit is provided that simply logs the error on STDERR.
  */
@implicitNotFound(
  "No ExceptionReporter was found in context for " +
  "reporting uncaught errors, either build one yourself or use " +
  "an implicit Scheduler (schedulers are ExceptionReporters)")
trait UncaughtExceptionReporter extends Serializable {
  def reportFailure(ex: Throwable): Unit
}

/** See [[UncaughtExceptionReporter]]. */
object UncaughtExceptionReporter {
  /** Builds a reporter out of the provided callback. */
  def apply(reporter: Throwable => Unit): UncaughtExceptionReporter =
    new UncaughtExceptionReporter {
      def reportFailure(ex: Throwable) = reporter(ex)
    }

  /** The default reporter. Simply prints stack traces on STDERR. */
  val LogExceptionsToStandardErr =
    UncaughtExceptionReporter(ExecutionContext.defaultReporter)
}