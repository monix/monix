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
 
package monix.concurrent

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


object extensions {
  /**
    * Provides utility methods added on Scala's `concurrent.Future`
    */
  implicit class FutureExtensions[T](val source: Future[T]) extends AnyVal {
    /** Utility that returns a new Future that either completes with
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
    def timeout(atMost: FiniteDuration)(implicit s: Scheduler): Future[T] =
      FutureUtils.timeout(source, atMost)

    /** Utility that returns a new Future that either completes with
      * the source's result or after the timeout specified by
      * `atMost` it tries to complete with the given `fallback`.
      * Whatever `Future` finishes first after the timeout, will win.
      *
      * @param atMost specifies the maximum wait time until the future is
      *               terminated with a TimeoutException
      *
      * @param fallback the fallback future that gets triggered after timeout
      *
      * @param s is the Scheduler, needed for completing our internal promise
      *
      * @return a new future that will either complete with the result of our
      *         source or with the fallback in case the timeout is reached
      */
    def timeout[U >: T](atMost: FiniteDuration, fallback: => Future[U])
      (implicit s: Scheduler): Future[U] =
      FutureUtils.timeout(source, atMost, fallback)

    /** Utility that lifts a `Future[T]` into a `Future[Try[T]]`, just because
      * it is useful sometimes.
      */
    def liftTry(implicit ec: ExecutionContext): Future[Try[T]] =
      FutureUtils.liftTry(source)
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
