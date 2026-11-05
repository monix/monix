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
import monix.execution.atomic.{ AtomicAny, AtomicInt }

import monix.execution.CancelableFuture.{ Async, Pure }
import monix.execution.cancelables.{ ChainedCancelable, SingleAssignCancelable }
import monix.execution.misc.Local
import monix.execution.schedulers.TrampolinedRunnable
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

/** Represents an asynchronous computation with a cancellation request.
  *
  * `cancel()` is non-blocking. Completion observes the process's terminal
  * outcome, including `CancellationException` after cancellation and cleanup
  * finish. Recovery can handle that exception like any other failure.
  * Builders wrapping external processes must supply their completion signal.
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

  override final def transform[S](s: (A) => S, f: (Throwable) => Throwable)(implicit
    executor: ExecutionContext
  ): CancelableFuture[S] =
    transform {
      case Success(a) => Success(s(a))
      case Failure(e) => Failure(f(e))
    }

  override final def map[S](f: (A) => S)(implicit executor: ExecutionContext): CancelableFuture[S] =
    transform {
      case Success(a) => Success(f(a))
      case fail => fail.asInstanceOf[Try[S]]
    }

  override final def filter(p: (A) => Boolean)(implicit executor: ExecutionContext): CancelableFuture[A] =
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

  /** Combines both inputs and waits for both to terminate. A failure cancels
    * the sibling; cancelling the result cancels both. The first observed
    * failure is preserved and a distinct sibling failure is suppressed on it.
    * Ordinary futures are adapted with [[CancelableFuture.fromFuture]].
    */
  override final def zip[U](that: Future[U]): CancelableFuture[(A, U)] = {
    CancelableFuture.zip(this, CancelableFuture.fromFuture(that))
  }

  override final def zipWith[U, R](that: Future[U])(f: (A, U) => R)(implicit
    executor: ExecutionContext
  ): CancelableFuture[R] =
    zip(that).map { case (a, b) => f(a, b) }

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

  override final def flatMap[S](f: (A) => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    transformWith {
      case Success(s) => f(s)
      case Failure(_) => this.asInstanceOf[CancelableFuture[S]]
    }

  def transform[S](f: Try[A] => Try[S])(implicit executor: ExecutionContext): CancelableFuture[S] = {
    transformWith(result => Future.fromTry(f(result)))
  }

  def transformWith[S](f: Try[A] => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] = {
    // Cancelable reference that needs to be chained with other
    // cancelable references created in the loop
    val cRef = ChainedCancelable(this)

    // FutureUtils will use a polyfill for Scala 2.11 and will
    // use the real `transformWith` on Scala 2.12
    val f2 = FutureUtils.transformWith(
      underlying,
      { (result: Try[A]) =>
        if (isolatedCtx ne null) Local.setContext(isolatedCtx)
        // A failure is an outcome that a consumer may recover from, not
        // a cancellation request for the consumer's replacement work.
        if (result.isFailure) cRef.reset()
        val next =
          try CancelableFuture.fromFuture(f(result))
          catch { case e if NonFatal(e) => CancelableFuture.failed(e) }

        if (next.isCompleted) cRef := Cancelable.empty
        else next.cancelable match {
          case nextChain: ChainedCancelable => nextChain.forwardTo(cRef)
          case token => cRef := token
        }

        // Forward tokens and unwrap futures so recursive composition does
        // not retain completed wrappers.
        next.underlying
      }
    )
    CancelableFuture.applyWithLocal(f2, cRef, isolatedCtx)
  }
}

object CancelableFuture extends internal.CancelableFutureForPlatform {
  /** Builder for a [[CancelableFuture]].
    *
    * The caller owns the cancellation protocol: `underlying` must report
    * cancellation only after the process and its finalizers have finished.
    * Invoking `cancelable` does not itself complete this future.
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

  /** Never completes normally. Cancellation immediately reports
    * [[java.util.concurrent.CancellationException]]. Each call creates
    * an independent cancellation scope.
    */
  final def never[A]: CancelableFuture[A] = {
    val promise = Promise[A]()
    apply(promise.future, Cancelable(() => { val _ = promise.tryFailure(new CancellationException); () }))
  }

  /** Adapts a future while preserving an existing cancelable future.
    *
    * Cancellation of an ordinary future cannot stop its producer. The
    * adapter waits for that producer to complete, then reports cancellation
    * instead of its result. The cancellation request itself does not block.
    */
  def fromFuture[A](source: Future[A]): CancelableFuture[A] =
    source match {
      case ref: CancelableFuture[A] => ref
      case _ =>
        source.value match {
          case Some(result) => fromTry(result)
          case None =>
            val promise = Promise[A]()
            // Pending, cancellation requested, completed.
            val state = AtomicInt(0)
            source.onComplete { result =>
              val outcome =
                if (state.getAndSet(2) == 1) Failure(new CancellationException)
                else result
              val _ = promise.tryComplete(outcome)
              ()
            }(immediate)
            apply(promise.future, Cancelable(() => { val _ = state.compareAndSet(0, 1); () }))
        }
    }

  /** Adapts a future with immediate cancellation of the adapter. The
    * inaccessible producer may continue running after cancellation.
    * Existing cancelable futures are returned unchanged.
    */
  def fromForgettableFuture[A](source: Future[A]): CancelableFuture[A] =
    source match {
      case ref: CancelableFuture[A] => ref
      case _ =>
        source.value match {
          case Some(result) => fromTry(result)
          case None =>
            val promise = Promise[A]()
            val listener = AtomicAny[Option[Promise[A]]](Some(promise))
            source.onComplete { result =>
              listener.getAndSet(None).foreach(_.tryComplete(result))
            }(immediate)
            apply(
              promise.future,
              Cancelable(() => {
                listener.getAndSet(None).foreach(_.tryFailure(new CancellationException))
              })
            )
        }
    }

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
  @deprecated("Use fromAsync with CompletionCallback", "4.0.0")
  def async[A](register: (Try[A] => Unit) => Cancelable)(implicit ec: ExecutionContext): CancelableFuture[A] =
    fromAsync(cb => register(cb.toTry))

  /** Starts an asynchronous process with explicit terminal notifications.
    *
    * The returned token requests cancellation. The producer must call
    * `onCancellation()` only after it has stopped and its finalizers have
    * finished. Returning from the token's `cancel()` does not complete the
    * future. A producer can instead report success or failure if that
    * outcome wins the race with cancellation.
    */
  def fromAsync[A](register: CompletionCallback[A] => Cancelable)(implicit
    ec: ExecutionContext
  ): CancelableFuture[A] = {

    val p = Promise[A]()
    val cRef = SingleAssignCancelable()

    // Light async boundary to guard against stack overflows
    ec.execute(new TrampolinedRunnable {
      def run(): Unit = {
        try {
          cRef := register(CompletionCallback.fromPromise(p))
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

  private[monix] def successfulWithLocal[A](value: A, isolatedCtx: Local.Context): CancelableFuture[A] =
    new Pure[A](Success(value), isolatedCtx)

  private[monix] def applyWithLocal[A](
    underlying: Future[A],
    cancelable: Cancelable,
    isolatedCtx: Local.Context
  ): CancelableFuture[A] =
    new Async[A](underlying, cancelable, isolatedCtx)

  private def zip[A, B](left: CancelableFuture[A], right: CancelableFuture[B]): CancelableFuture[(A, B)] = {
    val promise = Promise[(A, B)]()
    val leftToken = AtomicAny[Cancelable](left)
    val rightToken = AtomicAny[Cancelable](right)
    val lock = new AnyRef
    var leftResult: Option[Try[A]] = None
    var rightResult: Option[Try[B]] = None
    var firstFailure: Option[Throwable] = None

    def outcome(): Option[Try[(A, B)]] =
      (leftResult, rightResult) match {
        case (Some(a), Some(b)) =>
          val result = firstFailure match {
            case Some(error) =>
              a.failed.foreach(other => if (other ne error) error.addSuppressed(other))
              b.failed.foreach(other => if (other ne error) error.addSuppressed(other))
              Failure(error)
            case None =>
              for (x <- a; y <- b) yield (x, y)
          }
          leftResult = None
          rightResult = None
          firstFailure = None
          Some(result)
        case _ => None
      }

    def publish(result: Option[Try[(A, B)]]): Unit =
      result.foreach { value =>
        leftToken.set(Cancelable.empty)
        rightToken.set(Cancelable.empty)
        val _ = promise.tryComplete(value)
        ()
      }

    left.onComplete { result =>
      leftToken.set(Cancelable.empty)
      val completed = lock.synchronized {
        leftResult = Some(result)
        result match {
          case Failure(error) if firstFailure.isEmpty => firstFailure = Some(error)
          case _ => ()
        }
        outcome()
      }
      if (result.isFailure) rightToken.getAndSet(Cancelable.empty).cancel()
      publish(completed)
    }(immediate)

    right.onComplete { result =>
      rightToken.set(Cancelable.empty)
      val completed = lock.synchronized {
        rightResult = Some(result)
        result match {
          case Failure(error) if firstFailure.isEmpty => firstFailure = Some(error)
          case _ => ()
        }
        outcome()
      }
      if (result.isFailure) leftToken.getAndSet(Cancelable.empty).cancel()
      publish(completed)
    }(immediate)

    val token = new Cancelable {
      def cancel(): Unit =
        try leftToken.getAndSet(Cancelable.empty).cancel()
        finally rightToken.getAndSet(Cancelable.empty).cancel()
    }
    applyWithLocal(promise.future, token, left.isolatedCtx)
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

    def onComplete[U](f: (Try[A]) => U)(implicit executor: ExecutionContext): Unit =
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

    override def onComplete[U](f: (Try[A]) => U)(implicit executor: ExecutionContext): Unit = {
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
      if (!underlying.isCompleted) cancelable match {
        case chain: ChainedCancelable => immediate.execute(() => chain.cancel())
        case token => token.cancel()
      }
  }

}
