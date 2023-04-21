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

package monix.execution

import java.lang.Thread.UncaughtExceptionHandler

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
    "an implicit Scheduler (schedulers are ExceptionReporters)"
)
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

  /**
    * Default instance that logs errors in a platform specific way.
    *
    * For the JVM logging is accomplished using the current
    * [[https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.UncaughtExceptionHandler.html Thread.UncaughtExceptionHandler]].
    * If an `UncaughtExceptionHandler` is not currently set,
    * then error is printed on stderr.
    *
    * For JS logging is done via `console.error`.
    */
  val default: UncaughtExceptionReporter =
    internal.DefaultUncaughtExceptionReporter

  /**
    * DEPRECATED - use [[default]] instead.
    */
  @deprecated("Use UncaughtExceptionReporter.default", since = "3.0.0")
  val LogExceptionsToStandardErr = {
    // $COVERAGE-OFF$
    UncaughtExceptionReporter(ExecutionContext.defaultReporter)
    // $COVERAGE-ON$
  }

  implicit final class Extensions(val r: UncaughtExceptionReporter) extends AnyVal {
    /**
      * Converts [[UncaughtExceptionReporter]] to Java's
      * [[https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.UncaughtExceptionHandler.html UncaughtExceptionHandler]].
      */
    def asJava: UncaughtExceptionHandler =
      new UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable): Unit =
          r.reportFailure(e)
      }
  }
}
