/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import java.util.concurrent.CancellationException
import monix.execution.atomic.{ AtomicAny, AtomicBoolean }
import monix.execution.exceptions.{ CallbackCalledMultipleTimesException, UncaughtErrorException }
import monix.execution.schedulers.{ TrampolineExecutionContext, TrampolinedRunnable }
import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

/** Reports one terminal outcome of an asynchronous computation.
  *
  * Implementations must define how success, failure, and cancellation are
  * handled. A `CancellationException` passed to `onError` is an ordinary error;
  * cancellation is signaled only through `onCancellation`.
  */
trait CompletionCallback[-A] {
  /** Signals a successful value. */
  def onSuccess(value: A): Unit

  /** Signals an error. */
  def onError(error: Throwable): Unit

  /** Signals cancellation after the process has stopped and its finalizers
    * have finished executing. This reports termination; it does not request
    * cancellation of the callback's consumer.
    */
  def onCancellation(): Unit

  /** Signals a result using Scala's `Either` (`Left` is an error). */
  def apply(result: Either[Throwable, A]): Unit =
    result match {
      case Right(value) => onSuccess(value)
      case Left(error) => onError(error)
    }

  /** Signals a result using Scala's `Try`. */
  def apply(result: Try[A]): Unit =
    result match {
      case Success(value) => onSuccess(value)
      case Failure(error) => onError(error)
    }

  /** Returns a callback that maps successful values before forwarding them. */
  def contramap[B](f: B => A): CompletionCallback[B] =
    new CompletionCallback.Contramap(this, f)

  /** Attempts to signal a successful value. */
  def tryOnSuccess(value: A): Boolean =
    try {
      onSuccess(value)
      true
    } catch {
      case _: CallbackCalledMultipleTimesException => false
    }

  /** Attempts to signal an error. */
  def tryOnError(error: Throwable): Boolean =
    try {
      onError(error)
      true
    } catch {
      case _: CallbackCalledMultipleTimesException => false
    }

  /** Attempts to signal cancellation. */
  def tryOnCancellation(): Boolean =
    try {
      onCancellation()
      true
    } catch {
      case _: CallbackCalledMultipleTimesException => false
    }

  /** Attempts to signal a result using Scala's `Either`. */
  def tryApply(result: Either[Throwable, A]): Boolean =
    result match {
      case Right(value) => tryOnSuccess(value)
      case Left(error) => tryOnError(error)
    }

  /** Attempts to signal a result using Scala's `Try`. */
  def tryApply(result: Try[A]): Boolean =
    result match {
      case Success(value) => tryOnSuccess(value)
      case Failure(error) => tryOnError(error)
    }

  /** Adapts this callback to an `Either[Throwable, A] => Unit` function.
    * A `Left(new CancellationException(...))` passed to the returned function
    * is forwarded as an ordinary error.
    */
  def toAttempt: Either[Throwable, A] => Unit = {
    case Right(value) => onSuccess(value)
    case Left(error) => onError(error)
  }

  /** Adapts this callback to a `Try[A] => Unit` function.
    * A `Failure(new CancellationException(...))` passed to the returned
    * function is forwarded as an ordinary error.
    */
  def toTry: Try[A] => Unit = {
    case Success(value) => onSuccess(value)
    case Failure(error) => onError(error)
  }
}

object CompletionCallback {
  /** Wraps a callback with a thread-safe, at-most-once gate. */
  def safe[A](callback: CompletionCallback[A])(implicit reporter: UncaughtExceptionReporter): CompletionCallback[A] =
    new Safe(callback, reporter)

  /** Creates a callback that ignores success and cancellation and reports errors. */
  def empty[A](implicit reporter: UncaughtExceptionReporter): CompletionCallback[A] =
    new Empty(reporter)

  /** Creates a callback that completes the supplied promise. */
  def fromPromise[A](promise: Promise[A]): CompletionCallback[A] =
    new FromPromise(promise)

  /** Creates a callback from an `Either[Throwable, A] => Unit` function.
    * This adapter is not thread-safe.
    */
  def fromAttempt[A](callback: Either[Throwable, A] => Unit): CompletionCallback[A] =
    new FromAttempt(callback)

  /** Creates a callback from a `Try[A] => Unit` function.
    * This adapter is not thread-safe.
    */
  def fromTry[A](callback: Try[A] => Unit): CompletionCallback[A] =
    fromAttempt {
      case Right(value) => callback(Success(value))
      case Left(error) => callback(Failure(error))
    }

  /** Calls success, error, or cancellation asynchronously using a light
    * trampoline boundary. The returned callback is thread-safe.
    */
  def forked[A](callback: CompletionCallback[A])(implicit ec: ExecutionContext): CompletionCallback[A] =
    new AsyncFork(callback)

  /** Calls success, error, or cancellation asynchronously using a light
    * trampoline boundary. The returned callback is thread-safe.
    */
  def trampolined[A](callback: CompletionCallback[A])(implicit ec: ExecutionContext): CompletionCallback[A] =
    new Trampolined(callback)

  private[monix] def signalErrorTrampolined[A](callback: CompletionCallback[A], error: Throwable): Unit =
    TrampolineExecutionContext.immediate.execute(new Runnable {
      override def run(): Unit = callback.onError(error)
    })

  private def cancellationException(): CancellationException =
    new CancellationException("Callback cancelled")

  private def duplicate(method: String): CallbackCalledMultipleTimesException =
    new CallbackCalledMultipleTimesException(s"CompletionCallback.$method")

  private sealed trait Signal[+A]
  private object Signal {
    case object Pending extends Signal[Nothing]
    case object Consumed extends Signal[Nothing]
    final case class Succeeded[A](value: A) extends Signal[A]
    final case class Errored(error: Throwable) extends Signal[Nothing]
    case object Cancelled extends Signal[Nothing]
  }

  private final class Empty(reporter: UncaughtExceptionReporter) extends CompletionCallback[Any] {
    override def onSuccess(value: Any): Unit = ()
    override def onError(error: Throwable): Unit = reporter.reportFailure(UncaughtErrorException.wrap(error))
    override def onCancellation(): Unit = ()
  }

  private final class FromPromise[A](promise: Promise[A]) extends CompletionCallback[A] {
    override def onSuccess(value: A): Unit =
      if (!tryOnSuccess(value)) throw duplicate("onSuccess")

    override def onError(error: Throwable): Unit =
      if (!tryOnError(error)) throw new CallbackCalledMultipleTimesException("CompletionCallback.onError", error)

    override def onCancellation(): Unit =
      if (!tryOnCancellation()) throw duplicate("onCancellation")

    override def tryOnSuccess(value: A): Boolean = promise.trySuccess(value)
    override def tryOnError(error: Throwable): Boolean = promise.tryFailure(error)
    override def tryOnCancellation(): Boolean = promise.tryFailure(cancellationException())
    override def tryApply(result: Try[A]): Boolean = promise.tryComplete(result)
  }

  private final class FromAttempt[A](callback: Either[Throwable, A] => Unit) extends CompletionCallback[A] {
    private var isActive = true

    private def complete(result: Either[Throwable, A]): Boolean =
      if (isActive) {
        isActive = false
        callback(result)
        true
      } else {
        false
      }

    override def onSuccess(value: A): Unit =
      if (!tryOnSuccess(value)) throw CallbackCalledMultipleTimesException.forResult(Right(value))

    override def onError(error: Throwable): Unit =
      if (!tryOnError(error)) throw CallbackCalledMultipleTimesException.forResult(Left(error))

    override def onCancellation(): Unit =
      if (!tryOnCancellation()) throw duplicate("onCancellation")

    override def tryOnSuccess(value: A): Boolean = complete(Right(value))
    override def tryOnError(error: Throwable): Boolean = complete(Left(error))
    override def tryOnCancellation(): Boolean = complete(Left(cancellationException()))
    override def tryApply(result: Either[Throwable, A]): Boolean = complete(result)
  }

  private final class Safe[A](callback: CompletionCallback[A], reporter: UncaughtExceptionReporter)
    extends CompletionCallback[A] {
    private val active = AtomicBoolean(true)

    private def claim(method: String): Unit =
      if (!active.compareAndSet(true, false)) throw duplicate(method)

    override def onSuccess(value: A): Unit = {
      claim("onSuccess")
      try callback.onSuccess(value)
      catch {
        case e: CallbackCalledMultipleTimesException => throw e
        case e if NonFatal(e) => reporter.reportFailure(e)
      }
    }

    override def onError(error: Throwable): Unit = {
      if (!active.compareAndSet(true, false))
        throw new CallbackCalledMultipleTimesException("CompletionCallback.onError", UncaughtErrorException.wrap(error))
      try callback.onError(error)
      catch {
        case e: CallbackCalledMultipleTimesException => throw e
        case e if NonFatal(e) =>
          reporter.reportFailure(UncaughtErrorException.wrap(error))
          reporter.reportFailure(e)
      }
    }

    override def onCancellation(): Unit = {
      claim("onCancellation")
      try callback.onCancellation()
      catch {
        case e: CallbackCalledMultipleTimesException => throw e
        case e if NonFatal(e) => reporter.reportFailure(e)
      }
    }
  }

  private final class Contramap[A, B](callback: CompletionCallback[A], f: B => A) extends CompletionCallback[B] {
    override def onSuccess(value: B): Unit = callback.onSuccess(f(value))
    override def onError(error: Throwable): Unit = callback.onError(error)
    override def onCancellation(): Unit = callback.onCancellation()
  }

  private final class AsyncFork[A](callback: CompletionCallback[A])(implicit ec: ExecutionContext)
    extends AsyncBase(callback)

  private final class Trampolined[A](callback: CompletionCallback[A])(implicit ec: ExecutionContext)
    extends AsyncBase(callback) with TrampolinedRunnable

  private abstract class AsyncBase[A](callback: CompletionCallback[A])(implicit ec: ExecutionContext)
    extends CompletionCallback[A] with Runnable {
    private val state = AtomicAny(Signal.Pending: Signal[A])

    private def signal(result: Signal[A]): Boolean =
      if (state.compareAndSet(Signal.Pending, result)) {
        ec.execute(this)
        true
      } else {
        false
      }

    override final def onSuccess(value: A): Unit =
      if (!tryOnSuccess(value)) throw duplicate("onSuccess")

    override final def onError(error: Throwable): Unit =
      if (!tryOnError(error))
        throw new CallbackCalledMultipleTimesException("CompletionCallback.onError", UncaughtErrorException.wrap(error))

    override final def onCancellation(): Unit =
      if (!tryOnCancellation()) throw duplicate("onCancellation")

    override final def tryOnSuccess(value: A): Boolean = signal(Signal.Succeeded(value))
    override final def tryOnError(error: Throwable): Boolean = signal(Signal.Errored(error))
    override final def tryOnCancellation(): Boolean = signal(Signal.Cancelled)

    override final def run(): Unit =
      state.getAndSet(Signal.Consumed: Signal[A]) match {
        case Signal.Succeeded(value) => callback.onSuccess(value)
        case Signal.Errored(error) => callback.onError(error)
        case Signal.Cancelled => callback.onCancellation()
        case Signal.Pending => throw new IllegalStateException("CompletionCallback is not signaled")
        case Signal.Consumed => throw new IllegalStateException("CompletionCallback signal was already consumed")
      }
  }
}
