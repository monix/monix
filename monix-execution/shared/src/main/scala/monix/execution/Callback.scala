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
  * Obviously `Callback` describes unsafe side-effects, a fact that is
  * highlighted by the usage of `Unit` as the return type. Obviously
  * callbacks are unsafe to use in pure code, but are necessary for
  * describing asynchronous processes.
  */
abstract class Callback[-E, -A] extends (Either[E, A] => Unit) {

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
  def contramap[B](f: B => A): Callback[E, B] =
    new Callback.Contramap(this, f)
}

object Callback {
  /**
    * For building [[Callback]] objects using the
    * [[https://typelevel.org/cats/guidelines.html#partially-applied-type-params Partially-Applied Type]]
    * technique.
    *
    * For example these are Equivalent:
    *
    * `Callback[Throwable, Throwable].empty[String] <-> Callback.empty[Throwable, String]`
    */
  def apply[E]: Builders[E] = new Builders[E]

  /** Wraps any [[Callback]] into a safer implementation that
    * protects against grammar violations (e.g. `onSuccess` or `onError`
    * must be called at most once). For usage in `runAsync`.
    */
  def safe[E, A](cb: Callback[E, A])(implicit r: UncaughtExceptionReporter): Callback[E, A] =
    cb match {
      case _: SafeBCallback[_, _] => cb
      case _ => new SafeBCallback[E, A](cb)
    }

  /** Creates an empty [[Callback]], a callback that doesn't do
    * anything in `onNext` and that logs errors in `onError` with
    * the provided [[monix.execution.UncaughtExceptionReporter]].
    */
  def empty[E, A](implicit r: UncaughtExceptionReporter): Callback[E, A] =
    new EmptyBCallback(r)

  /** Returns a [[Callback]] instance that will complete the given
    * promise.
    */
  def fromPromise[A](p: Promise[A]): Callback[Throwable, A] =
    new Callback[Throwable, A] {
      def onSuccess(value: A): Unit = p.success(value)
      def onError(e: Throwable): Unit = p.failure(e)
    }

  /** Given a [[Callback]] wraps it into an implementation that
    * calls `onSuccess` and `onError` asynchronously, using the
    * given [[scala.concurrent.ExecutionContext]].
    *
    * The async boundary created is "light", in the sense that a
    * [[monix.execution.schedulers.TrampolinedRunnable TrampolinedRunnable]]
    * is used and supporting schedulers can execute these using an internal
    * trampoline, thus execution being faster and immediate, but still avoiding
    * growing the call-stack and thus avoiding stack overflows.
    *
    * @see [[Callback.trampolined[A](cb* trampolined]]
    */
  def forked[E, A](cb: Callback[E, A])(implicit ec: ExecutionContext): Callback[E, A] =
    new AsyncForkCallback(cb)

  /** Given a [[Callback]] wraps it into an implementation that
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
  def trampolined[E, A](cb: Callback[E, A])(implicit ec: ExecutionContext): Callback[E, A] =
    new TrampolinedCallback(cb)

  /** Turns `Either[Throwable, A] => Unit` callbacks into Monix
    * callbacks.
    *
    * These are common within Cats' implementation, used for
    * example in `cats.effect.IO`.
    */
  def fromAttempt[E, A](cb: Either[E, A] => Unit): Callback[E, A] = {
    if (cb.isInstanceOf[Callback[_, _]]) {
      cb.asInstanceOf[Callback[E, A]]
    } else {
      new Callback[E, A] {
        def onSuccess(value: A): Unit = cb(Right(value))
        def onError(e: E): Unit = cb(Left(e))
        override def apply(result: Either[E, A]): Unit = cb(result)
      }
    }
  }

  /** Turns `Try[A] => Unit` callbacks into Monix callbacks.
    *
    * These are common within Scala's standard library implementation,
    * due to usage with Scala's `Future`.
    */
  def fromTry[A](cb: Try[A] => Unit): Callback[Throwable, A] =
    new Callback[Throwable, A] {
      def onSuccess(value: A): Unit = cb(Success(value))
      def onError(ex: Throwable): Unit = cb(Failure(ex))
      override def apply(result: Try[A])(implicit ev: <:<[Throwable, Throwable]): Unit =
        cb(result)
    }

  /** Functions exposed via [[apply]]. */
  class Builders[E](val ev: Unit = ()) extends AnyVal {
    /** See [[Callback.safe]]. */
    final def safe[A](cb: Callback[E, A])(implicit r: UncaughtExceptionReporter): Callback[E, A] =
      Callback.safe(cb)

    /** See [[Callback.empty]]. */
    final def empty[A](implicit r: UncaughtExceptionReporter): Callback[E, A] =
      Callback.empty

    /** See [[Callback.fromPromise]]. */
    final def fromPromise[A](p: Promise[A])(implicit ev: Throwable <:< E): Callback[Throwable, A] =
      Callback.fromPromise(p)

    /** See [[Callback.forked]]. */
    final def forked[A](cb: Callback[E, A])(implicit ec: ExecutionContext): Callback[E, A] =
      Callback.forked(cb)

    /** See [[Callback.trampolined]]. */
    final def trampolined[A](cb: Callback[E, A])(implicit ec: ExecutionContext): Callback[E, A] =
      Callback.trampolined(cb)

    /** See [[Callback.fromAttempt]]. */
    final def fromAttempt[A](cb: Either[E, A] => Unit): Callback[E, A] =
      Callback.fromAttempt(cb)

    /** See [[Callback.fromTry]]. */
    final def fromTry[A](cb: Try[A] => Unit)(implicit ev: Throwable <:< E): Callback[Throwable, A] =
      Callback.fromTry(cb)
  }

  /**
    * Extension methods for [[Callback]].
    */
  implicit final class Extensions[-E, -A](val source: Callback[E, A])
    extends AnyVal {

    def apply(value: Try[A])(implicit ev: Throwable <:< E): Unit =
      value match {
        case Success(a) => source.onSuccess(a)
        case Failure(e) => source.onError(e)
      }
  }

  private[monix] def callSuccess[E, A](cb: Either[E, A] => Unit, value: A): Unit =
    cb match {
      case ref: Callback[E, A] @unchecked => ref.onSuccess(value)
      case _ => cb(Right(value))
    }

  private[monix] def callError[E, A](cb: Either[E, A] => Unit, value: E): Unit =
    cb match {
      case ref: Callback[E, A] @unchecked => ref.onError(value)
      case _ => cb(Left(value))
    }

  private final class AsyncForkCallback[E, A](cb: Callback[E, A])
    (implicit ec: ExecutionContext)
    extends Base[E, A](cb)(ec)

  private final class TrampolinedCallback[E, A](cb: Callback[E, A])
    (implicit ec: ExecutionContext)
    extends Base[E, A](cb)(ec) with TrampolinedRunnable

  /** Base implementation for `trampolined` and `forked`. */
  private[monix] class Base[E, A](cb: Callback[E, A])
    (implicit ec: ExecutionContext)
    extends Callback[E, A] with Runnable {

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
    extends Callback[Any, Any] {

    def onSuccess(value: Any): Unit = ()
    def onError(error: Any): Unit =
      r.reportFailure(UncaughtErrorException.wrap(error))
  }

  /** A `SafeCallback` is a callback that ensures it can only be called
    * once, with a simple check.
    */
  private final class SafeBCallback[-E, -A](underlying: Callback[E, A])
    (implicit r: UncaughtExceptionReporter)
    extends Callback[E, A] {

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

  private final class Contramap[-E, -A, -B](underlying: Callback[E, A], f: B => A)
    extends Callback[E, B] {

    def onSuccess(value: B): Unit =
      underlying.onSuccess(f(value))
    def onError(error: E): Unit =
      underlying.onError(error)
  }

  /** Contravariant type class instance of [[Callback]] for Cats. */
  implicit def contravariantCallback[E]: Contravariant[Callback[E, ?]] =
    contravariantRef.asInstanceOf[Contravariant[Callback[E, ?]]]

  private[this] val contravariantRef: Contravariant[Callback[Any, ?]] =
    new Contravariant[Callback[Any, ?]] {
      override def contramap[A, B](cb: Callback[Any, A])(f: B => A): Callback[Any, B] =
        cb.contramap(f)
    }
}
