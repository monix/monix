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

package monix.execution

import java.util.concurrent.TimeoutException

import monix.execution.misc.NonFatal

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/** Utilities for Scala's standard `concurrent.Future`. */
object FutureUtils {
  /** Utility that returns a new Future that either completes with
    * the original Future's result or with a TimeoutException in case
    * the maximum wait time was exceeded.
    *
    * @param atMost specifies the maximum wait time until the future is
    *               terminated with a TimeoutException
    * @param s is the Scheduler, needed for completing our internal promise
    * @return a new future that will either complete with the result of our
    *         source or fail in case the timeout is reached.
    */
  def timeout[T](source: Future[T], atMost: FiniteDuration)(implicit s: Scheduler): Future[T] = {
    val err = new TimeoutException
    val promise = Promise[T]()
    val task = s.scheduleOnce(atMost.length, atMost.unit,
      new Runnable { def run() = promise.tryFailure(err) })

    source.onComplete { r =>
      // canceling task to prevent waisted CPU resources and memory leaks
      // if the task has been executed already, this has no effect
      task.cancel()
      promise.tryComplete(r)
    }

    promise.future
  }

  /** Utility that returns a new Future that either completes with
    * the original Future's result or after the timeout specified by
    * `atMost` it tries to complete with the given `fallback`.
    * Whatever `Future` finishes first after the timeout, will win.
    *
    * @param atMost specifies the maximum wait time until the future is
    *               terminated with a TimeoutException
    * @param fallback the fallback future that gets triggered after timeout
    * @param s is the Scheduler, needed for completing our internal promise
    * @return a new future that will either complete with the result of our
    *         source or with the fallback in case the timeout is reached
    */
  def timeoutTo[T](source: Future[T], atMost: FiniteDuration, fallback: => Future[T])
    (implicit s: Scheduler): Future[T] = {

    val promise = Promise[T]()
    val task = s.scheduleOnce(atMost.length, atMost.unit,
      new Runnable { def run() = promise.tryCompleteWith(fallback) })

    source.onComplete { r =>
      // canceling task to prevent waisted CPU resources and memory leaks
      // if the task has been executed already, this has no effect
      task.cancel()
      promise.tryComplete(r)
    }

    promise.future
  }

  /** Utility that lifts a `Future[T]` into a `Future[Try[T]]`, exposing
    * error explicitly.
    */
  def materialize[T](source: Future[T])(implicit ec: ExecutionContext): Future[Try[T]] = {
    if (source.isCompleted) {
      Future.successful(source.value.get)
    }
    else {
      val p = Promise[Try[T]]()
      source.onComplete(result => p.success(result))
      p.future
    }
  }

  /** Given a mapping functions that operates on successful results as well as
    * errors, transforms the source by applying it.
    *
    * Similar to `Future.transform` from Scala 2.12.
    */
  def transform[T,S](source: Future[T], f: Try[T] => Try[S])(implicit ec: ExecutionContext): Future[S] = {
    val p = Promise[S]()
    source.onComplete { result =>
      val b = try f(result) catch { case NonFatal(t) => Failure(t) }
      p.complete(b)
    }
    p.future
  }

  /** Given a mapping functions that operates on successful results
    * as well as errors, transforms the source by applying it.
    *
    * Similar to `Future.transformWith` from Scala 2.12.
    */
  def transformWith[T,S](source: Future[T], f: Try[T] => Future[S])(implicit ec: ExecutionContext): Future[S] = {
    val p = Promise[S]()
    source.onComplete { result =>
      val fb = try f(result) catch { case NonFatal(t) => Future.failed(t) }
      if (fb eq source)
        p.complete(result.asInstanceOf[Try[S]])
      else fb.value match {
        case Some(value) => p.complete(value)
        case None => p.completeWith(fb)
      }
    }
    p.future
  }

  /** Utility that transforms a `Future[Try[T]]` into a `Future[T]`,
    * hiding errors, being the opposite of [[materialize]].
    */
  def dematerialize[T](source: Future[Try[T]])(implicit ec: ExecutionContext): Future[T] = {
    if (source.isCompleted)
      source.value.get match {
        case Failure(error) => Future.failed(error)
        case Success(value) => value match {
          case Success(success) => Future.successful(success)
          case Failure(error) => Future.failed(error)
        }
      }
    else {
      val p = Promise[T]()
      source.onComplete {
        case Failure(error) => p.failure(error)
        case Success(result) => p.complete(result)
      }
      p.future
    }
  }

  /** Creates a future that completes with the specified `result`, but only
    * after the specified `delay`.
    */
  def delayedResult[T](delay: FiniteDuration)(result: => T)(implicit s: Scheduler): Future[T] = {
    val p = Promise[T]()
    s.scheduleOnce(delay.length, delay.unit,
      new Runnable { def run() = p.complete(Try(result)) })
    p.future
  }

  /** Provides extension methods for `Future`. */
  object extensions {
    /** Provides utility methods added on Scala's `concurrent.Future` */
    implicit class FutureExtensions[T](val source: Future[T]) extends AnyVal {
      /** [[FutureUtils.timeout]] exposed as an extension method. */
      def timeout(atMost: FiniteDuration)(implicit s: Scheduler): Future[T] =
        FutureUtils.timeout(source, atMost)

      /** [[FutureUtils.timeoutTo]] exposed as an extension method. */
      def timeoutTo[U >: T](atMost: FiniteDuration, fallback: => Future[U])
        (implicit s: Scheduler): Future[U] =
        FutureUtils.timeoutTo(source, atMost, fallback)

      /** [[FutureUtils.materialize]] exposed as an extension method. */
      def materialize(implicit ec: ExecutionContext): Future[Try[T]] =
        FutureUtils.materialize(source)

      /** [[FutureUtils.dematerialize]] exposed as an extension method. */
      def dematerialize[U](implicit ev: T <:< Try[U], ec: ExecutionContext): Future[U] =
        FutureUtils.dematerialize(source.asInstanceOf[Future[Try[U]]])

      /** [[FutureUtils.transformWith]] exposed as an extension method. */
      def transformWith[S](f: Try[T] => Future[S])(implicit ec: ExecutionContext): Future[S] =
        FutureUtils.transformWith(source, f)
    }

    /** Provides utility methods for Scala's `concurrent.Future` companion object. */
    implicit class FutureCompanionExtensions(val f: Future.type) extends AnyVal {
      /** [[FutureUtils.delayedResult]] exposed as an extension method. */
      def delayedResult[T](delay: FiniteDuration)(result: => T)(implicit s: Scheduler): Future[T] =
        FutureUtils.delayedResult(delay)(result)
    }
  }
}
