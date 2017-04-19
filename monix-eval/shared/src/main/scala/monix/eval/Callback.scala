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

package monix.eval

import monix.execution.misc.NonFatal
import monix.execution.{Listener, Scheduler, UncaughtExceptionReporter}

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

/** Represents a callback that should be called asynchronously
  * with the result of a computation. Used by [[monix.eval.Task Task]] to signal
  * the completion of asynchronous computations on `runAsync`.
  *
  * The `onSuccess` method should be called only once, with the successful
  * result, whereas `onError` should be called if the result is an error.
  */
abstract class Callback[-T] extends Listener[T] with ((Try[T]) => Unit) {
  def onSuccess(value: T): Unit
  def onError(ex: Throwable): Unit

  /** An alias for [[onSuccess]], inherited
    * from [[monix.execution.Listener]].
    */
  final def onValue(value: T): Unit =
    onSuccess(value)

  final def apply(result: Try[T]): Unit =
    result match {
      case Success(value) => onSuccess(value)
      case Failure(ex) => onError(ex)
    }

  final def apply(result: Coeval[T]): Unit =
    result.runAttempt match {
      case Coeval.Now(value) => onSuccess(value)
      case Coeval.Error(ex) => onError(ex)
    }
}

object Callback {
  /** Wraps any [[Callback]] into a safer implementation that
    * protects against grammar violations (e.g. `onSuccess` or `onError`
    * must be called at most once). For usage in `runAsync`.
    */
  def safe[T](cb: Callback[T])
    (implicit r: UncaughtExceptionReporter): Callback[T] =
    cb match {
      case _: SafeCallback[_] => cb
      case _ => new SafeCallback[T](cb)
    }

  /** Creates an empty [[Callback]], a callback that doesn't do
    * anything in `onNext` and that logs errors in `onError` with
    * the provided [[monix.execution.UncaughtExceptionReporter]].
    */
  def empty[T](implicit r: UncaughtExceptionReporter): Callback[T] =
    new EmptyCallback(r)

  /** Returns a [[Callback]] instance that will complete the given
    * promise.
    */
  def fromPromise[A](p: Promise[A]): Callback[A] =
    new Callback[A] {
      def onError(ex: Throwable): Unit = p.failure(ex)
      def onSuccess(value: A): Unit = p.success(value)
    }

  /** Given a [[Callback]] wraps it into an implementation that
    * calls `onSuccess` and `onError` asynchronously, using the
    * given `ExecutionContext`.
    */
  def async[A](cb: Callback[A])(implicit s: Scheduler): Callback[A] =
    new Callback[A] {
      def onSuccess(value: A): Unit =
        s.executeTrampolined(() => cb.onSuccess(value))
      def onError(ex: Throwable): Unit =
        s.executeTrampolined(() => cb.onError(ex))
    }

  /** Useful extension methods for [[Callback]]. */
  implicit final class Extensions[-A](val source: Callback[A]) extends AnyVal {
    /** Extension method that calls `onSuccess` asynchronously. */
    def asyncOnSuccess(value: A)(implicit s: Scheduler): Unit =
      s.executeTrampolined(() => source.onSuccess(value))

    /** Extension method that calls `onError` asynchronously. */
    def asyncOnError(ex: Throwable)(implicit s: Scheduler): Unit =
      s.executeTrampolined(() => source.onError(ex))

    /** Extension method that calls `apply` asynchronously. */
    def asyncApply(value: Coeval[A])(implicit s: Scheduler): Unit =
      s.executeTrampolined(() => source(value))

    /** Extension method that calls `apply` asynchronously. */
    def asyncApply(value: Try[A])(implicit s: Scheduler): Unit =
      s.executeTrampolined(() => source(value))
  }

  /** An "empty" callback instance doesn't do anything `onSuccess` and
    * only logs exceptions `onError`.
    */
  private final class EmptyCallback(r: UncaughtExceptionReporter)
    extends Callback[Any] {

    def onSuccess(value: Any): Unit = ()
    def onError(ex: Throwable): Unit =
      r.reportFailure(ex)
  }

  /** A `SafeCallback` is a callback that ensures it can only be called
    * once, with a simple check.
    */
  private final class SafeCallback[-T](underlying: Callback[T])
    (implicit r: UncaughtExceptionReporter)
    extends Callback[T] {

    private[this] var isActive = true

    /** To be called only once, on successful completion of a [[monix.eval.Task Task]] */
    def onSuccess(value: T): Unit =
      if (isActive) {
        isActive = false
        try underlying.onSuccess(value) catch {
          case NonFatal(ex) =>
            r.reportFailure(ex)
        }
      }

    /** To be called only once, on failure of a [[monix.eval.Task Task]] */
    def onError(ex: Throwable): Unit =
      if (isActive) {
        isActive = false
        try underlying.onError(ex) catch {
          case NonFatal(err) =>
            r.reportFailure(ex)
            r.reportFailure(err)
        }
      }
  }
}