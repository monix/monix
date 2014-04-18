package monifu.concurrent

import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import java.util.concurrent.TimeoutException

object extensions {
  /**
   * Provides utility methods added on Scala's `concurrent.Future`
   */
  implicit class FutureExtensions[T](val f: Future[T]) extends AnyVal {
    /**
     * Combinator that returns a new Future that either completes with
     * the original Future's result or with a TimeoutException in case
     * the maximum wait time was exceeded.
     *
     * @param atMost specifies the maximum wait time until the future is
     *               terminated with a TimeoutException
     * @param s is the implicit Scheduler, needed for completing our internal promise
     */
    def withTimeout(atMost: FiniteDuration)(implicit s: Scheduler): Future[T] = {
      // catching the exception here, for non-useless stack traces
      val err = Try(throw new TimeoutException)
      val promise = Promise[T]()
      val task = s.scheduleOnce(atMost, promise.tryComplete(err))

      f.onComplete { case r =>
        // canceling task to prevent waisted CPU resources and memory leaks
        // if the task has been executed already, this has no effect
        task.cancel()
        promise.tryComplete(r)
      }

      promise.future
    }

    /**
     * Utility that lifts a `Future[T]` into a `Future[Try[T]]`, just because
     * it is useful sometimes.
     */
    def liftTry(implicit ec: ExecutionContext): Future[Try[T]] = {
      val p = Promise[Try[T]]()
      f.onComplete { case result => p.success(result) }
      p.future
    }
  }

  /**
   * Provides utility methods for Scala's `concurrent.Future` companion object.
   */
  implicit class FutureCompanionExtensions(val f: Future.type) extends AnyVal {
    /**
     * Future that completes with the specified `result`, but only
     * after the specified `delay`.
     */
    def delayedResult[T](delay: FiniteDuration)(result: => T)(implicit s: Scheduler): Future[T] = {
      val p = Promise[T]()
      s.scheduleOnce(delay, p.success(result))
      p.future
    }
  }
}
