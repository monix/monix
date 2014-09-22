package monifu.concurrent

import java.util.concurrent.TimeoutException

import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration._
import scala.util.Try

object FutureUtils {
  /**
   * Utility that returns a new Future that either completes with
   * the original Future's result or with a TimeoutException in case
   * the maximum wait time was exceeded.
   *
   * @param atMost specifies the maximum wait time until the future is
   *               terminated with a TimeoutException
   *
   * @param s is the Scheduler, needed for completing our internal promise
   *
   * @return a new future that will either complete with the result of our
   *         source or fail in case the timeout is reached.
   */
  def withTimeout[T](source: Future[T], atMost: FiniteDuration)(implicit s: Scheduler): Future[T] = {
    // catching the exception here, for non-useless stack traces
    val err = Try(throw new TimeoutException)
    val promise = Promise[T]()
    val task = s.scheduleOnce(atMost, promise.tryComplete(err))

    source.onComplete { case r =>
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
  def liftTry[T](source: Future[T])(implicit ec: ExecutionContext): Future[Try[T]] = {
    if (source.isCompleted) {
      Future.successful(source.value.get)
    }
    else {
      val p = Promise[Try[T]]()
      source.onComplete { case result => p.success(result) }
      p.future
    }
  }

  /**
   * Returns a new `Future` that takes a minimum amount of time to execute,
   * specified by `atLeast`.
   *
   * @param atLeast the minimal duration that the returned future will take to complete.
   * @param s the implicit scheduler that handles the scheduling and the execution
   * @return a new `Future` whose execution time is within the specified bounds
   */
  def withMinDuration[T](source: Future[T], atLeast: FiniteDuration)(implicit s: Scheduler): Future[T] = {
    val start = System.nanoTime()
    val p = Promise[T]()

    source.onComplete {
      case result =>
        val remainingNanos = atLeast.toNanos - (System.nanoTime() - start)
        if (remainingNanos >= 1000000) {
          val remaining = math.round(remainingNanos / 1000000.0).millis
          s.scheduleOnce(remaining, p.complete(result))
        }
        else
          p.complete(result)
    }

    p.future
  }

  /**
   * Creates a future that completes with the specified `result`, but only
   * after the specified `delay`.
   */
  def delayedResult[T](delay: FiniteDuration)(result: => T)(implicit s: Scheduler): Future[T] = {
    val p = Promise[T]()
    s.scheduleOnce(delay, p.complete(Try(result)))
    p.future
  }
}
