/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monifu.concurrent

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


object extensions {
  /**
   * Provides utility methods added on Scala's `concurrent.Future`
   */
  implicit class FutureExtensions[T](val source: Future[T]) extends AnyVal {
    /**
     * Combinator that returns a new Future that either completes with
     * the original Future's result or with a TimeoutException in case
     * the maximum wait time was exceeded.
     *
     * @param atMost specifies the maximum wait time until the future is
     *               terminated with a TimeoutException
     * @param s is the Scheduler, needed for completing our internal promise
     *
     * @return a new future that will either complete with the result of our
     *         source or fail in case the timeout is reached.
     */
    def withTimeout(atMost: FiniteDuration)(implicit s: Scheduler): Future[T] =
      FutureUtils.withTimeout(source, atMost)

    /**
     * Utility that lifts a `Future[T]` into a `Future[Try[T]]`, just because
     * it is useful sometimes.
     */
    def liftTry(implicit ec: ExecutionContext): Future[Try[T]] =
      FutureUtils.liftTry(source)

    /**
     * Returns a new `Future` that takes a minimum amount of time to execute,
     * specified by `atLeast`.
     *
     * @param atLeast the minimal duration that the returned future will take to complete.
     * @param s the implicit scheduler that handles the scheduling and the execution
     * @return a new `Future` whose execution time is within the specified bounds
     */
    def withMinDuration(atLeast: FiniteDuration)(implicit s: Scheduler): Future[T] =
      FutureUtils.withMinDuration(source, atLeast)
  }

  /**
   * Provides utility methods for Scala's `concurrent.Future` companion object.
   */
  implicit class FutureCompanionExtensions(val f: Future.type) extends AnyVal {
    /**
     * Creates a future that completes with the specified `result`, but only
     * after the specified `delay`.
     */
    def delayedResult[T](delay: FiniteDuration)(result: => T)(implicit s: Scheduler): Future[T] =
      FutureUtils.delayedResult(delay)(result)
  }
}
