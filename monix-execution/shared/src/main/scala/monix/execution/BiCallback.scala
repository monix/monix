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

import cats.Contravariant
import monix.execution.exceptions.UncaughtErrorException
import monix.execution.schedulers.TrampolinedRunnable

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** Represents a callback that should be called asynchronously
  * with the result of a computation.
  *
  * The `onSuccess` method should be called only once, with the successful
  * result, whereas `onError` should be called if the result is an error.
  *
  * Obviously `BiCallback` describes unsafe side-effects, a fact that is
  * highlighted by the usage of `Unit` as the return type. Obviously
  * callbacks are unsafe to use in pure code, but are necessary for
  * describing asynchronous processes.
  */
abstract class BiCallback[-E, -A] extends (Either[E, A] => Unit) {

  def onSuccess(value: A): Unit

  def onError(e: E): Unit

  def apply(result: Either[E, A]): Unit =
    result match {
      case Right(a) => onSuccess(a)
      case Left(e) => onError(e)
    }

  def apply(result: Try[A])(implicit ev: Throwable <:< E): Unit =
    result match {
      case Success(a) => onSuccess(a)
      case Failure(e) => onError(e)
    }

  /** Return a new callback that will apply the supplied function
    * before passing the result into this callback.
    */
  def contramap[B](f: B => A): BiCallback[E, B] =
    new BiCallback.Contramap(this, f)
}

object BiCallback {
  /**
    * For building [[BiCallback]] objects using the
    * [[https://typelevel.org/cats/guidelines.html#partially-applied-type-params Partially-Applied Type]]
    * technique.
    *
    * For example these are Equivalent:
    *
    * `BiCallback[Throwable].empty[String] <-> BiCallback.empty[Throwable, String]`
    */
  def apply[E]: Builders[E] = new Builders[E]

  /** Wraps any [[BiCallback]] into a safer implementation that
    * protects against grammar violations (e.g. `onSuccess` or `onError`
    * must be called at most once). For usage in `runAsync`.
    */
  def safe[E, A](cb: BiCallback[E, A])(implicit r: UncaughtExceptionReporter): BiCallback[E, A] =
    cb match {
      case _: SafeBCallback[_, _] => cb
      case _ => new SafeBCallback[E, A](cb)
    }

  /** Creates an empty [[BiCallback]], a callback that doesn't do
    * anything in `onNext` and that logs errors in `onError` with
    * the provided [[monix.execution.UncaughtExceptionReporter]].
    */
  def empty[E, A](implicit r: UncaughtExceptionReporter): BiCallback[E, A] =
    new EmptyBCallback(r)

  /** Returns a [[BiCallback]] instance that will complete the given
    * promise.
    */
  def fromPromise[A](p: Promise[A]): BiCallback[Throwable, A] =
    new BiCallback[Throwable, A] {
      def onSuccess(value: A): Unit = p.success(value)
      def onError(e: Throwable): Unit = p.failure(e)
    }

  /** Given a [[BiCallback]] wraps it into an implementation that
    * calls `onSuccess` and `onError` asynchronously, using the
    * given [[scala.concurrent.ExecutionContext]].
    *
    * The async boundary created is "light", in the sense that a
    * [[monix.execution.schedulers.TrampolinedRunnable TrampolinedRunnable]]
    * is used and supporting schedulers can execute these using an internal
    * trampoline, thus execution being faster and immediate, but still avoiding
    * growing the call-stack and thus avoiding stack overflows.
    *
    * @see [[BiCallback.trampolined[A](cb* trampolined]]
    */
  def forked[E, A](cb: BiCallback[E, A])(implicit ec: ExecutionContext): BiCallback[E, A] =
    new AsyncForkBiCallback(cb)

  /** Given a [[BiCallback]] wraps it into an implementation that
    * calls `onSuccess` and `onError` asynchronously, using the
    * given [[scala.concurrent.ExecutionContext]].
    *
    * The async boundary created is "light", in the sense that a
    * [[monix.execution.schedulers.TrampolinedRunnable TrampolinedRunnable]]
    * is used and supporting schedulers can execute these using an internal
    * trampoline, thus execution being faster and immediate, but still avoiding
    * growing the call-stack and thus avoiding stack overflows.
    *
    * @see [[forked]]
    */
  def trampolined[E, A](cb: BiCallback[E, A])(implicit ec: ExecutionContext): BiCallback[E, A] =
    new TrampolinedCallback(cb)

  /** Turns `Either[Throwable, A] => Unit` callbacks into Monix
    * callbacks.
    *
    * These are common within Cats' implementation, used for
    * example in `cats.effect.IO`.
    */
  def fromAttempt[E, A](cb: Either[E, A] => Unit): BiCallback[E, A] =
    new BiCallback[E, A] {
      def onSuccess(value: A): Unit = cb(Right(value))
      def onError(e: E): Unit = cb(Left(e))
      override def apply(result: Either[E, A]): Unit = cb(result)
    }

  /** Turns `Try[A] => Unit` callbacks into Monix callbacks.
    *
    * These are common within Scala's standard library implementation,
    * due to usage with Scala's `Future`.
    */
  def fromTry[A](cb: Try[A] => Unit): BiCallback[Throwable, A] =
    new BiCallback[Throwable, A] {
      def onSuccess(value: A): Unit = cb(Success(value))
      def onError(ex: Throwable): Unit = cb(Failure(ex))
      override def apply(result: Try[A])(implicit ev: <:<[Throwable, Throwable]): Unit =
        cb(result)
    }

  /** Functions exposed via [[apply]]. */
  class Builders[E](val ev: Unit = ()) extends AnyVal {
    /** See [[BiCallback.safe]]. */
    final def safe[A](cb: BiCallback[E, A])(implicit r: UncaughtExceptionReporter): BiCallback[E, A] =
      BiCallback.safe(cb)

    /** See [[BiCallback.empty]]. */
    final def empty[A](implicit r: UncaughtExceptionReporter): BiCallback[E, A] =
      BiCallback.empty

    /** See [[BiCallback.fromPromise]]. */
    final def fromPromise[A](p: Promise[A])(implicit ev: Throwable <:< E): BiCallback[Throwable, A] =
      BiCallback.fromPromise(p)

    /** See [[BiCallback.forked]]. */
    final def forked[A](cb: BiCallback[E, A])(implicit ec: ExecutionContext): BiCallback[E, A] =
      BiCallback.forked(cb)

    /** See [[BiCallback.trampolined]]. */
    final def trampolined[A](cb: BiCallback[E, A])(implicit ec: ExecutionContext): BiCallback[E, A] =
      BiCallback.trampolined(cb)

    /** See [[BiCallback.fromAttempt]]. */
    final def fromAttempt[A](cb: Either[E, A] => Unit): BiCallback[E, A] =
      BiCallback.fromAttempt(cb)

    /** See [[BiCallback.fromTry]]. */
    final def fromTry[A](cb: Try[A] => Unit)(implicit ev: Throwable <:< E): BiCallback[Throwable, A] =
      BiCallback.fromTry(cb)
  }

  /**
    * Extension methods for [[BiCallback]].
    */
  implicit final class Extensions[-E, -A](val source: BiCallback[E, A])
    extends AnyVal {

    def apply(value: Try[A])(implicit ev: Throwable <:< E): Unit =
      value match {
        case Success(a) => source.onSuccess(a)
        case Failure(e) => source.onError(e)
      }
  }

  private final class AsyncForkBiCallback[E, A](cb: BiCallback[E, A])
    (implicit ec: ExecutionContext)
    extends Base[E, A](cb)(ec)

  private final class TrampolinedCallback[E, A](cb: BiCallback[E, A])
    (implicit ec: ExecutionContext)
    extends Base[E, A](cb)(ec) with TrampolinedRunnable

  /** Base implementation for `trampolined` and `forked`. */
  private[monix] class Base[E, A](cb: BiCallback[E, A])
    (implicit ec: ExecutionContext)
    extends BiCallback[E, A] with Runnable {

    private[this] var state = 0
    private[this] var value: A = _
    private[this] var error: E = _

    final def onSuccess(value: A): Unit = {
      if (state == 0) {
        state = 1
        this.value = value
        ec.execute(this)
      }
    }
    final def onError(e: E): Unit = {
      if (state == 0) {
        state = 2
        this.error = e
        ec.execute(this)
      } else {
        ec.reportFailure(UncaughtErrorException.wrap(e))
      }
    }
    def run() = {
      val e = error
      if (state == 1) {
        cb.onSuccess(value)
        value = null.asInstanceOf[A]
      } else {
        cb.onError(e)
        error = null.asInstanceOf[E]
      }
    }
  }

  /** An "empty" callback instance doesn't do anything `onSuccess` and
    * only logs exceptions `onError`.
    */
  private final class EmptyBCallback(r: UncaughtExceptionReporter)
    extends BiCallback[Any, Any] {

    def onSuccess(value: Any): Unit = ()
    def onError(error: Any): Unit =
      r.reportFailure(UncaughtErrorException.wrap(error))
  }

  /** A `SafeCallback` is a callback that ensures it can only be called
    * once, with a simple check.
    */
  private final class SafeBCallback[-E, -A](underlying: BiCallback[E, A])
    (implicit r: UncaughtExceptionReporter)
    extends BiCallback[E, A] {

    private[this] var isActive = true

    def onSuccess(value: A): Unit =
      if (isActive) {
        isActive = false
        try underlying.onSuccess(value) catch {
          case ex if NonFatal(ex) =>
            r.reportFailure(ex)
        }
      }

    def onError(error: E): Unit =
      if (isActive) {
        isActive = false
        try underlying.onError(error) catch {
          case err if NonFatal(err) =>
            r.reportFailure(UncaughtErrorException.wrap(error))
            r.reportFailure(err)
        }
      }
  }

  private final class Contramap[-E, -A, -B](underlying: BiCallback[E, A], f: B => A)
    extends BiCallback[E, B] {

    def onSuccess(value: B): Unit =
      underlying.onSuccess(f(value))
    def onError(error: E): Unit =
      underlying.onError(error)
  }

  /** Contravariant type class instance of [[BiCallback]] for Cats. */
  implicit def contravariantCallback[E]: Contravariant[BiCallback[E, ?]] =
    contravariantRef.asInstanceOf[Contravariant[BiCallback[E, ?]]]

  private[this] val contravariantRef: Contravariant[BiCallback[Any, ?]] =
    new Contravariant[BiCallback[Any, ?]] {
      override def contramap[A, B](cb: BiCallback[Any, A])(f: B => A): BiCallback[Any, B] =
        cb.contramap(f)
    }
}
