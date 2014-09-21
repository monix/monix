package monifu.concurrent

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

/**
 * An exception reporter is a function that logs an uncaught error.
 *
 * Usually taken as an implicit when executing computations that could fail,
 * but that must not blow up the call-stack, like asynchronous tasks.
 *
 * A default implicit is provided that simply logs the error on STDERR.
 */
@implicitNotFound(
  "No ExceptionReporter was found in context for " +
  "reporting uncaught errors, either build one yourself or use either " +
  "an implicit Scheduler (schedulers are ExceptionReporters) or " +
  "import monifu.concurrent.Scheduler.Implicits.defaultExceptionReporter")
trait UncaughtExceptionReporter {
  def reportFailure(ex: Throwable): Unit
}

/**
 * See [[UncaughtExceptionReporter]].
 */
object UncaughtExceptionReporter {
  /**
   * Builds a reporter out of the provided callback.
   */
  def apply(reporter: Throwable => Unit): UncaughtExceptionReporter =
    new UncaughtExceptionReporter {
      def reportFailure(ex: Throwable) = reporter(ex)
    }

  /**
   * The default reporter.
   * Simply prints stack traces on STDERR.
   */
  val LogExceptionsToStandardErr = {
    UncaughtExceptionReporter(ex => ExecutionContext.defaultReporter(ex))
  }
}