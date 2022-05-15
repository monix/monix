/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import monix.execution.exceptions.{ CallbackCalledMultipleTimesException, UncaughtErrorException }
import monix.execution.schedulers.{ TrampolineExecutionContext, TrampolinedRunnable }
import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

/** Represents a callback that should be called asynchronously
  * with the result of a computation.
  *
  * This is an `Either[E, A] => Unit` with an OOP interface that
  * avoids extra boxing, along with overloads of `apply`.
  *
  * The `onSuccess` method should be called only once, with the successful
  * result, whereas `onError` should be called if the result is an error.
  *
  * Obviously `Callback` describes unsafe side-effects, a fact that is
  * highlighted by the usage of `Unit` as the return type. Obviously
  * callbacks are unsafe to use in pure code, but are necessary for
  * describing asynchronous processes.
  *
  * THREAD-SAFETY: callback implementations are NOT thread-safe
  * by contract, this depends on the implementation. Callbacks
  * can be made easily thread-safe via wrapping with:
  *
  *  - [[monix.execution.Callback.safe Callback.safe]]
  *  - [[monix.execution.Callback.trampolined Callback.trampolined]]
  *  - [[monix.execution.Callback.forked Callback.forked]]
  *
  * NOTE that callbacks injected in the [[monix.eval.Task Task]] async
  * builders (e.g. [[monix.eval.Task.async Task.async]]) are thread-safe.
  *
  * @define safetyIssues Can be called at most once by contract.
  *         Not necessarily thread-safe, depends on implementation.
  *
  *         @throws CallbackCalledMultipleTimesException depending on
  *                 implementation, when signaling via this callback is
  *                 attempted multiple times and the protocol violation
  *                 is detected.
  *
  * @define callbackCalledMultipleTimes
  *         [[monix.execution.exceptions.CallbackCalledMultipleTimesException CallbackCalledMultipleTimesException]]
  *
  * @define tryMethodDescription In case the underlying callback
  *         implementation protects against protocol violations, then
  *         this method should return `false` in case the final result
  *         was already signaled once via [[onSuccess]] or
  *         [[onError]].
  *
  *         The default implementation relies on catching
  *         $callbackCalledMultipleTimes in case of violations, which
  *         is what thread-safe implementations of `onSuccess` or
  *         `onError` are usually throwing.
  *
  *         WARNING: this method is only provided as a
  *         convenience. The presence of this method does not
  *         guarantee that the underlying callback is thread-safe or
  *         that it protects against protocol violations.
  *
  *         @return `true` if the invocation completes normally or
  *                 `false` in case another concurrent call succeeded
  *                 first in signaling a result
  */
abstract class Callback[-E, -A] extends (Either[E, A] => Unit) {
  /**
    * Signals a successful value.
    *
    * $safetyIssues
    */
  def onSuccess(value: A): Unit

  /**
    * Signals an error.
    *
    * $safetyIssues
    */
  def onError(e: E): Unit

  /**
    * Signals a value via Scala's `Either` (`Left` is error, `Right` is
    * the successful value).
    *
    * $safetyIssues
    */
  def apply(result: Either[E, A]): Unit =
    result match {
      case Right(a) => onSuccess(a)
      case Left(e) => onError(e)
    }

  /**
    * Signals a value via Scala's `Try`.
    *
    * $safetyIssues
    */
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

  /**
    * Attempts to call [[Callback.onSuccess]].
    *
    * $tryMethodDescription
    */
  def tryOnSuccess(value: A): Boolean =
    try {
      onSuccess(value)
      true
    } catch {
      case _: CallbackCalledMultipleTimesException => false
    }

  /**
    * Attempts to call [[Callback.onError]].
    *
    * $tryMethodDescription
    */
  def tryOnError(e: E): Boolean =
    try {
      onError(e)
      true
    } catch {
      case _: CallbackCalledMultipleTimesException => false
    }

  /**
    * Attempts to call [[Callback.apply(result:scala\.util\.Try* Callback.apply]].
    *
    * $tryMethodDescription
    */
  def tryApply(result: Try[A])(implicit ev: Throwable <:< E): Boolean =
    result match {
      case Success(a) => tryOnSuccess(a)
      case Failure(e) => tryOnError(e)
    }

  /**
    * Attempts to call [[Callback.apply(result:Either* Callback.apply]].
    *
    * $tryMethodDescription
    */
  def tryApply(result: Either[E, A]): Boolean =
    result match {
      case Right(a) => tryOnSuccess(a)
      case Left(e) => tryOnError(e)
    }
}

/**
  * @define isThreadSafe '''THREAD-SAFETY''': the returned callback is
  *         thread-safe.
  *
  *         In case `onSuccess` and `onError` get called multiple times,
  *         from multiple threads even, the implementation protects against
  *         access violations and throws a
  *         [[monix.execution.exceptions.CallbackCalledMultipleTimesException CallbackCalledMultipleTimesException]].
  */
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
    * protects against protocol violations (e.g. `onSuccess` or `onError`
    * must be called at most once).
    *
    * $isThreadSafe
    */
  def safe[E, A](cb: Callback[E, A])(implicit r: UncaughtExceptionReporter): Callback[E, A] =
    cb match {
      case ref: Safe[E, A] @unchecked => ref
      case _ => new Safe[E, A](cb)
    }

  /** Creates an empty [[Callback]], a callback that doesn't do
    * anything in `onNext` and that logs errors in `onError` with
    * the provided [[monix.execution.UncaughtExceptionReporter]].
    */
  def empty[E, A](implicit r: UncaughtExceptionReporter): Callback[E, A] =
    new Empty(r)

  /** Returns a [[Callback]] instance that will complete the given
    * promise.
    *
    * THREAD-SAFETY: the provided instance is thread-safe by virtue
    * of `Promise` being thread-safe.
    */
  def fromPromise[A](p: Promise[A]): Callback[Throwable, A] =
    new Callback[Throwable, A] {
      override def tryApply(result: Try[A])(implicit ev: Throwable <:< Throwable): Boolean =
        p.tryComplete(result)
      override def tryOnSuccess(value: A): Boolean =
        p.trySuccess(value)
      override def tryOnError(e: Throwable): Boolean =
        p.tryFailure(e)
      override def onSuccess(value: A): Unit =
        if (!tryOnSuccess(value)) throw new CallbackCalledMultipleTimesException("onSuccess")
      override def onError(e: Throwable): Unit =
        if (!tryOnError(e)) throw new CallbackCalledMultipleTimesException("onError", e)
      override def apply(result: Try[A])(implicit ev: Throwable <:< Throwable): Unit =
        if (!tryApply(result)) throw CallbackCalledMultipleTimesException.forResult(result)
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
    * $isThreadSafe
    *
    * @see [[Callback.trampolined]]
    */
  def forked[E, A](cb: Callback[E, A])(implicit ec: ExecutionContext): Callback[E, A] =
    new AsyncFork(cb)

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
    * $isThreadSafe
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
    *
    * WARNING: the returned callback is NOT thread-safe!
    */
  def fromAttempt[E, A](cb: Either[E, A] => Unit): Callback[E, A] =
    cb match {
      case ref: Callback[E, A] @unchecked => ref
      case _ =>
        new Callback[E, A] {
          private[this] var isActive = true
          override def onSuccess(value: A): Unit = apply(Right(value))
          override def onError(e: E): Unit = apply(Left(e))

          override def apply(result: Either[E, A]): Unit =
            if (!tryApply(result)) {
              throw CallbackCalledMultipleTimesException.forResult(result)
            }

          override def tryApply(result: Either[E, A]): Boolean =
            if (isActive) {
              isActive = false
              cb(result)
              true
            } else {
              false
            }
        }
    }

  /** Turns `Try[A] => Unit` callbacks into Monix callbacks.
    *
    * These are common within Scala's standard library implementation,
    * due to usage with Scala's `Future`.
    *
    * WARNING: the returned callback is NOT thread-safe!
    */
  def fromTry[A](cb: Try[A] => Unit): Callback[Throwable, A] =
    new Callback[Throwable, A] {
      private[this] var isActive = true
      override def onSuccess(value: A): Unit = apply(Success(value))
      override def onError(e: Throwable): Unit = apply(Failure(e))

      override def apply(result: Try[A])(implicit ev: Throwable <:< Throwable): Unit =
        if (!tryApply(result)) {
          throw CallbackCalledMultipleTimesException.forResult(result)
        }

      override def tryApply(result: Try[A])(implicit ev: Throwable <:< Throwable): Boolean = {
        if (isActive) {
          isActive = false
          cb(result)
          true
        } else {
          false
        }
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

  private[monix] def signalErrorTrampolined[E, A](cb: Callback[E, A], e: E): Unit =
    TrampolineExecutionContext.immediate.execute(() => cb.onError(e))

  /** Functions exposed via [[apply]]. */
  final class Builders[E](val ev: Boolean = true) extends AnyVal {
    /** See [[Callback.safe]]. */
    def safe[A](cb: Callback[E, A])(implicit r: UncaughtExceptionReporter): Callback[E, A] =
      Callback.safe(cb)

    /** See [[Callback.empty]]. */
    def empty[A](implicit r: UncaughtExceptionReporter): Callback[E, A] =
      Callback.empty

    /** See [[Callback.fromPromise]]. */
    def fromPromise[A](p: Promise[A])(implicit ev: Throwable <:< E): Callback[Throwable, A] =
      Callback.fromPromise(p)

    /** See [[Callback.forked]]. */
    def forked[A](cb: Callback[E, A])(implicit ec: ExecutionContext): Callback[E, A] =
      Callback.forked(cb)

    /** See [[Callback.trampolined]]. */
    def trampolined[A](cb: Callback[E, A])(implicit ec: ExecutionContext): Callback[E, A] =
      Callback.trampolined(cb)

    /** See [[Callback.fromAttempt]]. */
    def fromAttempt[A](cb: Either[E, A] => Unit): Callback[E, A] =
      Callback.fromAttempt(cb)

    /** See [[Callback.fromTry]]. */
    def fromTry[A](cb: Try[A] => Unit)(implicit ev: Throwable <:< E): Callback[Throwable, A] =
      Callback.fromTry(cb)
  }

  private final class AsyncFork[E, A](cb: Callback[E, A])(implicit ec: ExecutionContext) extends Base[E, A](cb)(ec)

  private final class TrampolinedCallback[E, A](cb: Callback[E, A])(implicit ec: ExecutionContext)
    extends Base[E, A](cb)(ec) with TrampolinedRunnable

  /** Base implementation for `trampolined` and `forked`. */
  private class Base[E, A](cb: Callback[E, A])(implicit ec: ExecutionContext) extends Callback[E, A] with Runnable {
    private[this] val state = monix.execution.atomic.AtomicInt(0)
    private[this] var value: A = _
    private[this] var error: E = _

    override final def onSuccess(value: A): Unit =
      if (!tryOnSuccess(value)) {
        throw new CallbackCalledMultipleTimesException("onSuccess")
      }

    override final def tryOnSuccess(value: A): Boolean = {
      if (state.compareAndSet(0, 1)) {
        this.value = value
        ec.execute(this)
        true
      } else {
        false
      }
    }

    override final def onError(e: E): Unit =
      if (!tryOnError(e)) {
        throw new CallbackCalledMultipleTimesException(
          "Callback.onError",
          UncaughtErrorException.wrap(e)
        )
      }

    override final def tryOnError(e: E): Boolean = {
      if (state.compareAndSet(0, 2)) {
        this.error = e
        ec.execute(this)
        true
      } else {
        false
      }
    }

    override final def run(): Unit = {
      state.get() match {
        case 1 =>
          val v = value
          value = null.asInstanceOf[A]
          cb.onSuccess(v)
        case 2 =>
          val e = error
          error = null.asInstanceOf[E]
          cb.onError(e)
      }
    }
  }

  /** An "empty" callback instance doesn't do anything `onSuccess` and
    * only logs exceptions `onError`.
    */
  private final class Empty(r: UncaughtExceptionReporter) extends Callback[Any, Any] {
    def onSuccess(value: Any): Unit = ()
    def onError(error: Any): Unit =
      r.reportFailure(UncaughtErrorException.wrap(error))
  }

  /** A `SafeCallback` is a callback that ensures it can only be called
    * once, with a simple check.
    */
  private final class Safe[-E, -A](underlying: Callback[E, A])(implicit r: UncaughtExceptionReporter)
    extends Callback[E, A] {

    private[this] val isActive =
      monix.execution.atomic.AtomicBoolean(true)

    override def onSuccess(value: A): Unit = {
      if (isActive.compareAndSet(true, false))
        try {
          underlying.onSuccess(value)
        } catch {
          case e: CallbackCalledMultipleTimesException =>
            throw e
          case e if NonFatal(e) =>
            r.reportFailure(e)
        }
      else {
        throw new CallbackCalledMultipleTimesException("onSuccess")
      }
    }

    override def onError(e: E): Unit = {
      if (isActive.compareAndSet(true, false)) {
        try {
          underlying.onError(e)
        } catch {
          case e: CallbackCalledMultipleTimesException =>
            throw e
          case e2 if NonFatal(e2) =>
            r.reportFailure(UncaughtErrorException.wrap(e))
            r.reportFailure(e2)
        }
      } else {
        val ex = UncaughtErrorException.wrap(e)
        throw new CallbackCalledMultipleTimesException("onError", ex)
      }
    }
  }

  private final class Contramap[-E, -A, -B](underlying: Callback[E, A], f: B => A) extends Callback[E, B] {
    def onSuccess(value: B): Unit =
      underlying.onSuccess(f(value))
    def onError(error: E): Unit =
      underlying.onError(error)
  }
}
