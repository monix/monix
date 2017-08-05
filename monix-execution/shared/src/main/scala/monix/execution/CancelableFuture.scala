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

  // Needed for Scala 2.12 compatibility
  def transform[S](f: Try[A] => Try[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    CancelableFuture(FutureUtils.transform(this, f), this)

  // Needed for Scala 2.12 compatibility
  def transformWith[S](f: Try[A] => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    FutureUtils.transformWith(this, f)

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
    new Implementation[A](underlying, cancelable)

  /** Promotes a strict `value` to a [[CancelableFuture]] that's already complete. */
  def successful[A](value: A): CancelableFuture[A] =
    new Now[A](Success(value))

  /** Promotes a strict `Throwable` to a [[CancelableFuture]] that's already complete. */
  def failed[A](ex: Throwable): CancelableFuture[A] =
    new Now[A](Failure(ex))

  /** An already completed [[CancelableFuture]]. */
  final val unit: CancelableFuture[Unit] =
    successful(())

  /** Returns a [[CancelableFuture]] instance that will never complete. */
  final def never[A]: CancelableFuture[A] =
    Never

  /** Promotes a strict `Try[A]` to a [[CancelableFuture]] that's already complete. */
  def fromTry[A](value: Try[A]): CancelableFuture[A] =
    new Now[A](value)

  /** A [[CancelableFuture]] instance that will never complete. */
  private object Never extends CancelableFuture[Nothing] {
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
  }

  /** An internal [[CancelableFuture]] implementation. */
  private final class Now[+A](immediate: Try[A]) extends CancelableFuture[A] {
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
  }

  /** An actual [[CancelableFuture]] implementation; internal. */
  private final class Implementation[+A](underlying: Future[A], cancelable: Cancelable)
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
  }

  implicit def catsInstances(implicit ec: ExecutionContext): Monad[CancelableFuture] =
    new Monad[CancelableFuture] with StackSafeMonad[CancelableFuture] with CoflatMap[CancelableFuture] with MonadError[CancelableFuture, Throwable] {
      def pure[A](x: A) = CancelableFuture.successful(x)
      override def map[A, B](fa: CancelableFuture[A])(f: A => B) = fa.map(f)
      def flatMap[A, B](fa: CancelableFuture[A])(f: A => CancelableFuture[B]) = fa.flatMap(f)
      def coflatMap[A, B](fa: CancelableFuture[A])(f: CancelableFuture[A] => B): CancelableFuture[B] = CancelableFuture(Future(f(fa)), fa)
      def handleErrorWith[A](fa: CancelableFuture[A])(f: Throwable => CancelableFuture[A]) = fa.recoverWith { case t => f(t) }
      def raiseError[A](e: Throwable) = CancelableFuture.failed(e)
      override def handleError[A](fea: CancelableFuture[A])(f: Throwable => A) = fea.recover { case t => f(t) }
      override def attempt[A](fa: CancelableFuture[A]) =
        (fa.map(a => Right[Throwable, A](a))) recover { case NonFatal(t) => Left(t) }
      override def recover[A](fa: CancelableFuture[A])(pf: PartialFunction[Throwable, A]) = fa.recover(pf)
      override def recoverWith[A](fa: CancelableFuture[A])(pf: PartialFunction[Throwable, CancelableFuture[A]]) = fa.recoverWith(pf)
      override def catchNonFatal[A](a: => A)(implicit ev: Throwable <:< Throwable) = CancelableFuture(Future(a), Cancelable.empty)
      override def catchNonFatalEval[A](a: Eval[A])(implicit ev: Throwable <:< Throwable) = CancelableFuture(Future(a.value), Cancelable.empty)
    }

}
