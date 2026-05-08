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

import monix.execution.Cancelable.IsDummy
import monix.execution.CancelableFuture.Async
import monix.execution.CancelableFuture.Never
import monix.execution.CancelableFuture.Pure
import monix.execution.cancelables.ChainedCancelable
import monix.execution.cancelables.SingleAssignCancelable
import monix.execution.misc.Local
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import monix.execution.schedulers.TrampolinedRunnable

import java.util.concurrent.TimeoutException
import scala.concurrent.*
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

/** Represents an asynchronous computation that can be canceled
  * as long as it isn't complete.
  */
sealed abstract class CancelableFuture[+A] extends Future[A] with Cancelable { self =>
  /** Returns this future's underlying [[Cancelable]] reference. */
  private[monix] def cancelable: Cancelable

  /** Returns the underlying `Future` reference. */
  private[monix] def underlying: Future[A]

  /** Returns this future's underlying [[Local]] reference. */
  private[monix] def isolatedCtx: Local.Context = null

  override final def failed: CancelableFuture[Throwable] = {
    implicit val ec = immediate
    transformWith {
      case Success(_) =>
        CancelableFuture.failed(new NoSuchElementException("failed"))
      case Failure(e) =>
        CancelableFuture.successful(e)
    }
  }

  override final def transform[S](s: A => S, f: Throwable => Throwable)(implicit
    executor: ExecutionContext
  ): CancelableFuture[S] =
    transform {
      case Success(a) => Success(s(a))
      case Failure(e) => Failure(f(e))
    }

  override final def map[S](f: A => S)(implicit executor: ExecutionContext): CancelableFuture[S] =
    transform {
      case Success(a) => Success(f(a))
      case fail => fail.asInstanceOf[Try[S]]
    }

  override final def filter(p: A => Boolean)(implicit executor: ExecutionContext): CancelableFuture[A] =
    transform {
      case Success(a) if !p(a) =>
        throw new NoSuchElementException("Future.filter predicate is not satisfied")
      case pass =>
        pass
    }

  override final def collect[S](pf: PartialFunction[A, S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    transform {
      case Success(a) =>
        if (pf.isDefinedAt(a)) Success(pf(a))
        else
          throw new NoSuchElementException("Future.collect partial function is not defined at: " + a)
      case fail @ Failure(_) =>
        fail.asInstanceOf[Failure[S]]
    }

  override final def recover[U >: A](pf: PartialFunction[Throwable, U])(implicit
    executor: ExecutionContext
  ): CancelableFuture[U] =
    transform {
      case ref @ Success(_) => ref
      case Failure(e) =>
        if (!pf.isDefinedAt(e)) throw e
        Success(pf(e))
    }

  override final def recoverWith[U >: A](pf: PartialFunction[Throwable, Future[U]])(implicit
    executor: ExecutionContext
  ): CancelableFuture[U] =
    transformWith {
      case Success(_) => this
      case Failure(e) =>
        if (!pf.isDefinedAt(e)) this
        else pf(e)
    }

  override final def zip[U](that: Future[U]): CancelableFuture[(A, U)] = {
    implicit val ec = immediate
    for (a <- this; b <- that) yield (a, b)
  }

  override final def fallbackTo[U >: A](that: Future[U]): CancelableFuture[U] = {
    implicit val ec = immediate
    transformWith {
      case Success(_) => this
      case Failure(_) => that
    }
  }

  override final def mapTo[S](implicit tag: ClassTag[S]): CancelableFuture[S] = {
    this match {
      case Async(other, cRef, local) =>
        CancelableFuture.applyWithLocal(other.mapTo[S], cRef, local)
      case p: Pure[Any] =>
        CancelableFuture.applyWithLocal(super.mapTo[S], Cancelable.empty, p.isolatedCtx)
      case Never =>
        Never
    }
  }

  override final def andThen[U](pf: PartialFunction[Try[A], U])(implicit
    executor: ExecutionContext
  ): CancelableFuture[A] =
    transformWith { r =>
      if (pf.isDefinedAt(r)) {
        val _ = pf(r)
      }
      this
    }

  override final def flatMap[S](f: A => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    transformWith {
      case Success(s) => f(s)
      case Failure(_) => this.asInstanceOf[CancelableFuture[S]]
    }

  def transform[S](f: Try[A] => Try[S])(implicit executor: ExecutionContext): CancelableFuture[S] = {
    val g: Try[A] => Try[S] = result => {
      if (isolatedCtx ne null) Local.setContext(isolatedCtx)
      f(result)
    }
    val next = FutureUtils.transform(underlying, g)
    CancelableFuture.applyWithLocal(next, cancelable, isolatedCtx)
  }

  def transformWith[S](f: Try[A] => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] = {
    // Cancelable reference that needs to be chained with other
    // cancelable references created in the loop
    val cRef = ChainedCancelable(cancelable)

    // FutureUtils will use a polyfill for Scala 2.11 and will
    // use the real `transformWith` on Scala 2.12
    val f2 = FutureUtils.transformWith(
      underlying,
      { (result: Try[A]) =>
        if (isolatedCtx ne null) Local.setContext(isolatedCtx)
        val nextRef: Future[S] =
          try f(result)
          catch { case e if NonFatal(e) => Future.failed(e) }

        // Checking to see if we are dealing with a "flatMap"
        // future, in which case we need to chain the cancelable
        // reference in order to not create a memory leak
        nextRef match {
          case ref: CancelableFuture[?] if ref ne Never =>
            val cf = ref.asInstanceOf[CancelableFuture[S]]
            // If the resulting Future is completed, there's no reason
            // to chain cancelable tokens
            if (!cf.isCompleted)
              cf.cancelable match {
                case cRef2: ChainedCancelable =>
                  // Chaining ensures we don't leak
                  cRef2.forwardTo(cRef)
                case cRef2 =>
                  if (!cRef2.isInstanceOf[IsDummy]) cRef := cRef2
              }

            // Returning underlying b/c otherwise we leak memory in
            // infinite loops
            cf.underlying

          case _ =>
            nextRef
        }
      }
    )
    CancelableFuture.applyWithLocal(f2, cRef, isolatedCtx)
  }

  /** Applies a timeout to the current computation, which will be canceled if
    * it doesn't complete within the specified duration.
    *
    * A timed-out future will be complete with an `TimeoutException`.
    *
    * @param atMost the maximum duration to wait for the computation to complete
    * @param sc the scheduler to use for scheduling the timeout
    * @return a `CancelableFuture` that will complete with the result of the
    *         computation if it finishes within the timeout, or fail with a
    *         `TimeoutException` if the timeout is exceeded
    */
  def timeout(atMost: Duration)(implicit sc: Scheduler): CancelableFuture[A] =
    timeoutTo(atMost, Failure(new TimeoutException(s"Timed out after $atMost")))

  /**
    * Applies a timeout to the current computation, which will be canceled if
    * it doesn't complete within the specified `atMost` duration. If the
    * computation times out, the resulting future will be completed with the
    * provided `fallback` value.
    *
    * @param atMost   the maximum duration to wait for the computation to complete
    * @param fallback the fallback value to use if the computation times out
    * @param sc       the scheduler to use for scheduling the timeout
    * @return a `CancelableFuture` that will complete with the result of the
    *         computation if it finishes within the timeout, or with the provided
    *         fallback value if the timeout is exceeded
    */
  def timeoutTo[U >: A](atMost: Duration, fallback: Try[U])(implicit sc: Scheduler): CancelableFuture[U] =
    atMost match {
      case fd: FiniteDuration =>
        val timeout = CancelableFuture.delayedResult(fd)(())
        CancelableFuture.race(timeout, this).transform {
          case Failure(e) => Failure(e)
          case Success(Right(r)) => Success(r)
          case Success(Left(_)) => fallback
        }
      case _ =>
        this
    }
}

object CancelableFuture extends internal.CancelableFutureForPlatform {
  /** Builder for a [[CancelableFuture]].
    *
    * @param underlying is an underlying `Future` reference that will respond to `onComplete` calls
    * @param cancelable is a [[monix.execution.Cancelable Cancelable]]
    *        that can be used to cancel the active computation
    */
  def apply[A](underlying: Future[A], cancelable: Cancelable): CancelableFuture[A] =
    new Async[A](underlying, cancelable)

  /** Promotes a strict `value` to a [[CancelableFuture]] that's
    * already complete.
    *
    * @param value is the value that's going to be signaled in the
    *        `onComplete` callback.
    */
  def successful[A](value: A): CancelableFuture[A] =
    new Pure[A](Success(value))

  /** Promotes a strict `Throwable` to a [[CancelableFuture]] that's
    * already complete with a failure.
    *
    * @param e is the error that's going to be signaled in the
    *        `onComplete` callback.
    */
  def failed[A](e: Throwable): CancelableFuture[A] =
    new Pure[A](Failure(e))

  /** Promotes a strict `value` to a [[CancelableFuture]] that's
    * already complete.
    *
    * Alias for [[successful]].
    *
    * @param value is the value that's going to be signaled in the
    *        `onComplete` callback.
    */
  def pure[A](value: A): CancelableFuture[A] =
    successful(value)

  /** Promotes a strict `Throwable` to a [[CancelableFuture]] that's
    * already complete with a failure.
    *
    * Alias for [[failed]].
    *
    * @param e is the error that's going to be signaled in the
    *        `onComplete` callback.
    */
  def raiseError[A](e: Throwable): CancelableFuture[A] =
    failed(e)

  /** An already completed [[CancelableFuture]]. */
  val unit: CancelableFuture[Unit] =
    successful(())

  /** Returns a [[CancelableFuture]] instance that will never complete. */
  final def never[A]: CancelableFuture[A] =
    Never

  /** Promotes a strict `Try[A]` to a [[CancelableFuture]] that's
    * already complete.
    *
    * @param value is the `Try[A]` value that's going to be signaled
    *        in the `onComplete` callback.
    */
  def fromTry[A](value: Try[A]): CancelableFuture[A] =
    new Pure[A](value)

  /** Given a registration function that can execute an asynchronous
    * process, executes it and builds a [[CancelableFuture]] value
    * out of it.
    *
    * The given `registration` function can return a [[Cancelable]]
    * reference that can be used to cancel the executed async process.
    * This reference can be [[Cancelable.empty empty]].
    *
    * {{{
    *   def delayedResult[A](f: => A)(implicit s: Scheduler): CancelableFuture[A] =
    *     CancelableFuture.async { complete =>
    *       val task = s.scheduleOnce(10.seconds) { complete(Try(f)) }
    *
    *       Cancelable { () =>
    *         println("Cancelling!")
    *         task.cancel()
    *       }
    *     }
    * }}}
    *
    * This is much like working with Scala's
    * [[scala.concurrent.Promise Promise]], only safer.
    */
  def async[A](register: (Try[A] => Unit) => Cancelable)(implicit ec: ExecutionContext): CancelableFuture[A] = {

    val p = Promise[A]()
    val cRef = SingleAssignCancelable()

    // Light async boundary to guard against stack overflows
    ec.execute(new TrampolinedRunnable() {
      def run() = {
        try {
          cRef := register { v => p.complete(v); () }
          ()
        } catch {
          case e if NonFatal(e) =>
            if (!p.tryComplete(Failure(e)))
              ec.reportFailure(e)
        }
      }
    })
    CancelableFuture(p.future, cRef)
  }

  /** Creates a future that completes with the specified `result`, but only
    * after the specified `delay`.
    */
  def delayedResult[A](delay: FiniteDuration)(result: => A)(implicit sc: Scheduler): CancelableFuture[A] = {
    val p = Promise[A]()
    val token = sc.scheduleOnce(delay.length, delay.unit, () => p.complete(Try(result)))
    CancelableFuture(p.future, token)
  }

  /**
    * Returns a new [[CancelableFuture]] that will complete with the result
    * of the first of the futures to complete. The losing computation will be
    * canceled.
    *
    * @param fa the first [[CancelableFuture]] to participate in the race
    * @param fb the second [[CancelableFuture]] to participate in the race
    * @param ec the [[ExecutionContext]] needed for registering callbacks
    *
    * @return a new [[CancelableFuture]] that will complete with the result of
    *         the first future to complete, wrapped in `Left` if it was `fa`,
    *         or `Right` if it was `fb`. The losing future will be canceled.
    */
  def race[A, B](
    fa: CancelableFuture[A],
    fb: CancelableFuture[B]
  )(implicit ec: ExecutionContext): CancelableFuture[Either[A, B]] = {
    val promise = Promise[Either[A, B]]()
    fa.onComplete { resultA =>
      if (promise.tryComplete(resultA.map(Left.apply)))
        fb.cancel()
    }
    fb.onComplete { resultB =>
      if (promise.tryComplete(resultB.map(Right.apply)))
        fa.cancel()
    }
    CancelableFuture(
      promise.future,
      Cancelable.collection(fa.cancelable, fb.cancelable)
    )
  }

  private[monix] def successfulWithLocal[A](value: A, isolatedCtx: Local.Context): CancelableFuture[A] =
    new Pure[A](Success(value), isolatedCtx)

  private[monix] def applyWithLocal[A](
    underlying: Future[A],
    cancelable: Cancelable,
    isolatedCtx: Local.Context
  ): CancelableFuture[A] =
    new Async[A](underlying, cancelable, isolatedCtx)

  /** A [[CancelableFuture]] instance that will never complete. */
  private[execution] object Never extends CancelableFuture[Nothing] {
    def onComplete[U](f: Try[Nothing] => U)(implicit executor: ExecutionContext): Unit = ()

    val isCompleted = false
    val value = None

    override def cancelable = Cancelable.empty
    override def underlying = this
    override val isolatedCtx: Local.Context = null

    @scala.throws[Exception](classOf[Exception])
    def result(atMost: Duration)(implicit permit: CanAwait): Nothing =
      throw new TimeoutException("This CancelableFuture will never finish!")

    @scala.throws[InterruptedException](classOf[InterruptedException])
    @scala.throws[TimeoutException](classOf[TimeoutException])
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type =
      throw new TimeoutException("This CancelableFuture will never finish!")

    def cancel(): Unit = ()

    override def transform[S](f: Try[Nothing] => Try[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
      this
    override def transformWith[S](f: Try[Nothing] => Future[S])(implicit
      executor: ExecutionContext
    ): CancelableFuture[S] =
      this
  }

  /** An internal [[CancelableFuture]] implementation. */
  private[execution] final class Pure[+A](immediate: Try[A], override val isolatedCtx: Local.Context = null)
    extends CancelableFuture[A] {
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    def result(atMost: Duration)(implicit permit: CanAwait): A = immediate.get

    def cancelable = Cancelable.empty
    val underlying = Future.fromTry(immediate)

    def cancel(): Unit = ()
    def isCompleted: Boolean = true
    def value: Option[Try[A]] = underlying.value

    def onComplete[U](f: Try[A] => U)(implicit executor: ExecutionContext): Unit =
      executor.execute(() => {
        val _ = f(immediate)
        ()
      })
  }

  /** An actual [[CancelableFuture]] implementation; internal. */
  private[execution] final case class Async[+A](
    underlying: Future[A],
    cancelable: Cancelable,
    override val isolatedCtx: Local.Context = null
  ) extends CancelableFuture[A] {

    override def onComplete[U](f: Try[A] => U)(implicit executor: ExecutionContext): Unit = {
      underlying.onComplete(f)(executor)
    }
    override def isCompleted: Boolean =
      underlying.isCompleted
    override def value: Option[Try[A]] =
      underlying.value

    @throws[Exception](classOf[Exception])
    def result(atMost: Duration)(implicit permit: CanAwait): A =
      underlying.result(atMost)(permit)

    @throws[InterruptedException](classOf[InterruptedException])
    @throws[TimeoutException](classOf[TimeoutException])
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
      underlying.ready(atMost)(permit)
      this
    }

    override def cancel(): Unit =
      cancelable.cancel()
  }

}
