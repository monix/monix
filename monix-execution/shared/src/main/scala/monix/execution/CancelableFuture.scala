/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import cats.{CoflatMap, Eval, Monad, MonadError, StackSafeMonad}
import monix.execution.Cancelable.IsDummy
import monix.execution.cancelables.ChainedCancelable
import monix.execution.schedulers.TrampolinedRunnable

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

/** Represents an asynchronous computation that can be canceled
  * as long as it isn't complete.
  */
trait CancelableFuture[+A] extends Future[A] with Cancelable {
  // Overriding methods for getting CancelableFuture in return

  // Abstract
  def transform[S](f: Try[A] => Try[S])(implicit executor: ExecutionContext): CancelableFuture[S]
  // Abstract
  def transformWith[S](f: Try[A] => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S]

  override def failed: CancelableFuture[Throwable] =
    CancelableFuture(super.failed, this)
  override def transform[S](s: (A) => S, f: (Throwable) => Throwable)(implicit executor: ExecutionContext): CancelableFuture[S] =
    CancelableFuture(super.transform(s, f), this)
  override def map[S](f: (A) => S)(implicit executor: ExecutionContext): CancelableFuture[S] =
    CancelableFuture(super.map(f), this)
  override def filter(p: (A) => Boolean)(implicit executor: ExecutionContext): CancelableFuture[A] =
    CancelableFuture(super.filter(p), this)
  override def collect[S](pf: PartialFunction[A, S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    CancelableFuture(super.collect(pf), this)
  override def recover[U >: A](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): CancelableFuture[U] =
    CancelableFuture(super.recover(pf), this)
  override def recoverWith[U >: A](pf: PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): CancelableFuture[U] =
    CancelableFuture(super.recoverWith(pf), this)
  override def zip[U](that: Future[U]): CancelableFuture[(A, U)] =
    CancelableFuture(super.zip(that), this)
  override def fallbackTo[U >: A](that: Future[U]): CancelableFuture[U] =
    CancelableFuture(super.fallbackTo(that), this)
  override def mapTo[S](implicit tag: ClassTag[S]): CancelableFuture[S] =
    CancelableFuture(super.mapTo(tag), this)
  override def andThen[U](pf: PartialFunction[Try[A], U])(implicit executor: ExecutionContext): CancelableFuture[A] =
    CancelableFuture(super.andThen(pf), this)

  override def flatMap[S](f: (A) => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    // trick borrowed from Scala 2.12
    transformWith {
      case Success(s) => f(s)
      case Failure(_) => this.asInstanceOf[CancelableFuture[S]]
    }
}

object CancelableFuture {
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
    new Now[A](Success(value))

  /** Promotes a strict `Throwable` to a [[CancelableFuture]] that's
    * already complete with a failure.
    *
    * @param e is the error that's going to be signaled in the
    *        `onComplete` callback.
    */
  def failed[A](e: Throwable): CancelableFuture[A] =
    new Now[A](Failure(e))

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
  final val unit: CancelableFuture[Unit] =
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
    new Now[A](value)

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
  def async[A](register: (Try[A] => Unit) => Cancelable)
    (implicit ec: ExecutionContext): CancelableFuture[A] = {

    val p = Promise[A]()
    val cRef = ChainedCancelable()

    // Light async boundary to guard against stack overflows
    ec.execute(new TrampolinedRunnable {
      def run(): Unit = {
        try {
          register(p.complete) match {
            case _: IsDummy => ()
            case cRef2: ChainedCancelable => cRef2.chainTo(cRef)
            case ref => cRef := ref
          }
        } catch {
          case NonFatal(e) =>
            if (!p.tryComplete(Failure(e)))
              ec.reportFailure(e)
        }
      }
    })

    CancelableFuture(p.future, cRef)
  }

  /** A [[CancelableFuture]] instance that will never complete. */
  private[execution] object Never extends CancelableFuture[Nothing] {
    def onComplete[U](f: (Try[Nothing]) => U)
      (implicit executor: ExecutionContext): Unit = ()

    val isCompleted = false
    val value = None

    @scala.throws[Exception](classOf[Exception])
    def result(atMost: Duration)(implicit permit: CanAwait): Nothing =
      throw new TimeoutException("This CancelableFuture will never finish!")

    @scala.throws[InterruptedException](classOf[InterruptedException])
    @scala.throws[TimeoutException](classOf[TimeoutException])
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type =
      throw new TimeoutException("This CancelableFuture will never finish!")

    def cancel(): Unit = ()

    // Overriding everything in order to avoid memory leaks

    override def transform[S](f: (Try[Nothing]) => Try[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
      this
    override def transformWith[S](f: (Try[Nothing]) => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
      this
    override def failed: CancelableFuture[Throwable] =
      this
    override def transform[S](s: (Nothing) => S, f: (Throwable) => Throwable)(implicit executor: ExecutionContext): CancelableFuture[S] =
      this
    override def map[S](f: (Nothing) => S)(implicit executor: ExecutionContext): CancelableFuture[S] =
      this
    override def filter(p: (Nothing) => Boolean)(implicit executor: ExecutionContext): CancelableFuture[Nothing] =
      this
    override def collect[S](pf: PartialFunction[Nothing, S])(implicit executor: ExecutionContext): CancelableFuture[S] =
      this
    override def recover[U >: Nothing](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): CancelableFuture[U] =
      this
    override def recoverWith[U >: Nothing](pf: PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): CancelableFuture[U] =
      this
    override def zip[U](that: Future[U]): CancelableFuture[(Nothing, U)] =
      this
    override def fallbackTo[U >: Nothing](that: Future[U]): CancelableFuture[U] =
      this
    override def mapTo[S](implicit tag: ClassTag[S]): CancelableFuture[S] =
      this
    override def andThen[U](pf: PartialFunction[Try[Nothing], U])(implicit executor: ExecutionContext): CancelableFuture[Nothing] =
      this
    override def flatMap[S](f: (Nothing) => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
      this
  }

  /** An internal [[CancelableFuture]] implementation. */
  private[execution]  final class Now[+A](immediate: Try[A]) extends CancelableFuture[A] {
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    def result(atMost: Duration)(implicit permit: CanAwait): A = immediate.get

    def cancel(): Unit = ()
    def isCompleted: Boolean = true
    val value: Some[Try[A]] = Some(immediate)

    def onComplete[U](f: (Try[A]) => U)(implicit executor: ExecutionContext): Unit =
      executor.execute(new Runnable { def run(): Unit = f(immediate) })

    // Overriding methods for getting CancelableFuture in return
    override def failed: CancelableFuture[Throwable] =
      immediate match {
        case Success(_) => new Now(Failure(new NoSuchElementException("failed")))
        case Failure(ex) => new Now(Success(ex))
      }

    override def transform[S](f: (Try[A]) => Try[S])(implicit executor: ExecutionContext): CancelableFuture[S] = {
      val p = Promise[S]()
      executor.execute(new Runnable {
        def run(): Unit =
          p.complete(try f(immediate) catch { case NonFatal(e) => Failure(e) })
      })
      CancelableFuture(p.future, Cancelable.empty)
    }

    override def transformWith[S](f: (Try[A]) => Future[S])
      (implicit executor: ExecutionContext): CancelableFuture[S] =
      internalTransformWith(Future.fromTry(immediate), Cancelable.empty, f)
  }

  /** An actual [[CancelableFuture]] implementation; internal. */
  private[execution] final case class Async[+A](underlying: Future[A], cancelable: Cancelable)
    extends CancelableFuture[A] {

    override def onComplete[U](f: (Try[A]) => U)(implicit executor: ExecutionContext): Unit =
      underlying.onComplete(f)(executor)
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

    override def transform[S](f: (Try[A]) => Try[S])(implicit executor: ExecutionContext): CancelableFuture[S] = {
      val next = FutureUtils.transform(underlying, f)
      CancelableFuture(next, cancelable)
    }

    // Overriding `transformWith` with an implementation that takes
    // the class of the returned `Future` reference into account
    // in order to chain cancellable future references
    override def transformWith[S](f: (Try[A]) => Future[S])
      (implicit executor: ExecutionContext): CancelableFuture[S] =
      internalTransformWith(underlying, cancelable, f)
  }

  private def internalTransformWith[A, S](
    underlying: Future[A], cancelable: Cancelable, f: (Try[A]) => Future[S])
    (implicit executor: ExecutionContext): CancelableFuture[S] = {

    // Cancelable reference that needs to be chained with other
    // cancelable references created in the loop
    val cRef = ChainedCancelable(cancelable)

    // FutureUtils will use a polyfill for Scala 2.11 and will
    // use the real `transformWith` on Scala 2.12
    val f2 = FutureUtils.transformWith(underlying, { result: Try[A] =>
      val nextRef =
        try f(result)
        catch { case NonFatal(e) => Future.failed(e) }

      if (!nextRef.isCompleted) {
        // Checking to see if we are dealing with a "flatMap"
        // future, in which case we need to chain the cancelable
        // reference in order to not create a memory leak
        nextRef match {
          case Async(_, cancelable2) =>
            cancelable2 match {
              case cRef2: ChainedCancelable =>
                // This chaining will make it that all updates to cRef2
                // will be forwarded to cRef or to the end of the chain,
                // therefore this frees these refs in flatMap loops to
                // be garbage collected by the garbage collector
                cRef2.chainTo(cRef)
              case cRef2 =>
                cRef := cRef2
            }
          case cf: CancelableFuture[_] =>
            cRef := cf
          case _ =>
            () // do nothing
        }
      }

      nextRef
    })

    CancelableFuture(f2, cRef)
  }

  /** Returns the associated Cats type class instances for the
    * [[CancelableFuture]] data type.
    *
    * @param ec is the
    *        [[scala.concurrent.ExecutionContext ExecutionContext]]
    *        needed in order to create the needed type class instances,
    *        since future transformations rely on an `ExecutionContext`
    *        passed explicitly (by means of an implicit parameter)
    *        on each operation
    */
  implicit def catsInstances(implicit ec: ExecutionContext): CatsInstances =
    new CatsInstances()

  /** Implementation of Cats type classes for the
    * [[CancelableFuture]] data type.
    *
    * @param ec is the
    *        [[scala.concurrent.ExecutionContext ExecutionContext]]
    *        needed since future transformations rely on an
    *        `ExecutionContext` passed explicitly (by means of an
    *        implicit parameter) on each operation
    */
  final class CatsInstances(implicit ec: ExecutionContext)
    extends Monad[CancelableFuture]
    with StackSafeMonad[CancelableFuture]
    with CoflatMap[CancelableFuture]
    with MonadError[CancelableFuture, Throwable] {

    override def pure[A](x: A): CancelableFuture[A] =
      CancelableFuture.successful(x)
    override def map[A, B](fa: CancelableFuture[A])(f: A => B): CancelableFuture[B] =
      fa.map(f)
    override def flatMap[A, B](fa: CancelableFuture[A])(f: A => CancelableFuture[B]): CancelableFuture[B] =
      fa.flatMap(f)
    override def coflatMap[A, B](fa: CancelableFuture[A])(f: CancelableFuture[A] => B): CancelableFuture[B] =
      CancelableFuture(Future(f(fa)), fa)
    override def handleErrorWith[A](fa: CancelableFuture[A])(f: Throwable => CancelableFuture[A]): CancelableFuture[A] =
      fa.recoverWith { case t => f(t) }
    override def raiseError[A](e: Throwable): CancelableFuture[Nothing] =
      CancelableFuture.failed(e)
    override def handleError[A](fea: CancelableFuture[A])(f: Throwable => A): CancelableFuture[A] =
      fea.recover { case t => f(t) }
    override def attempt[A](fa: CancelableFuture[A]): CancelableFuture[Either[Throwable, A]] =
      fa.transformWith(liftToEitherRef).asInstanceOf[CancelableFuture[Either[Throwable, A]]]
    override def recover[A](fa: CancelableFuture[A])(pf: PartialFunction[Throwable, A]): CancelableFuture[A] =
      fa.recover(pf)
    override def recoverWith[A](fa: CancelableFuture[A])(pf: PartialFunction[Throwable, CancelableFuture[A]]): CancelableFuture[A] =
      fa.recoverWith(pf)
    override def catchNonFatal[A](a: => A)(implicit ev: Throwable <:< Throwable): CancelableFuture[A] =
      CancelableFuture(Future(a), Cancelable.empty)
    override def catchNonFatalEval[A](a: Eval[A])(implicit ev: Throwable <:< Throwable): CancelableFuture[A] =
      CancelableFuture(Future(a.value), Cancelable.empty)

    override def adaptError[A](fa: CancelableFuture[A])(pf: PartialFunction[Throwable, Throwable]): CancelableFuture[A] =
      fa.transformWith {
        case Failure(e) if pf.isDefinedAt(e) =>
          Future.failed(pf(e))
        case _ =>
          fa
      }
  }

  // Reusable reference to use in `CatsInstances.attempt`
  private[this] final val liftToEitherRef: (Try[Any] => CancelableFuture[Either[Throwable, Any]]) =
    tryA => new Now(Success(tryA match {
      case Success(a) => Right(a)
      case Failure(e) => Left(e)
    }))
}
