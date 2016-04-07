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

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Represents an asynchronous computation that can be canceled
  * as long as it isn't complete.
  */
trait CancelableFuture[+T] extends Future[T] with Cancelable {
  // Overriding methods for getting CancelableFuture in return

  override def failed: CancelableFuture[Throwable] =
    CancelableFuture.wrap(this).failed
  override def transform[S](s: (T) => S, f: (Throwable) => Throwable)(implicit executor: ExecutionContext): CancelableFuture[S] =
    CancelableFuture.wrap(this).transform(s, f)
  override def map[S](f: (T) => S)(implicit executor: ExecutionContext): CancelableFuture[S] =
    CancelableFuture.wrap(this).map(f)
  override def flatMap[S](f: (T) => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    CancelableFuture.wrap(this).flatMap(f)
  override def filter(p: (T) => Boolean)(implicit executor: ExecutionContext): CancelableFuture[T] =
    CancelableFuture.wrap(this).filter(p)
  override def collect[S](pf: PartialFunction[T, S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    CancelableFuture.wrap(this).collect(pf)
  override def recover[U >: T](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): CancelableFuture[U] =
    CancelableFuture.wrap(this).recover(pf)
  override def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): CancelableFuture[U] =
    CancelableFuture.wrap(this).recoverWith(pf)
  override def zip[U](that: Future[U]): CancelableFuture[(T, U)] =
    CancelableFuture.wrap(this).zip(that)
  override def fallbackTo[U >: T](that: Future[U]): CancelableFuture[U] =
    CancelableFuture.wrap(this).fallbackTo(that)
  override def mapTo[S](implicit tag: ClassTag[S]): CancelableFuture[S] =
    CancelableFuture.wrap(this).mapTo(tag)
  override def andThen[U](pf: PartialFunction[Try[T], U])(implicit executor: ExecutionContext): CancelableFuture[T] =
    CancelableFuture.wrap(this).andThen(pf)
}

object CancelableFuture {
  /** Builder for a [[CancelableFuture]].
    *
    * @param underlying is an underlying `Future` reference that will respond to `onComplete` calls
    * @param cancelable is a [[monix.execution.Cancelable Cancelable]]
    *        that can be used to cancel the active computation
    */
  def apply[T](underlying: Future[T], cancelable: Cancelable): CancelableFuture[T] =
    new Implementation[T](underlying, cancelable)

  /** Promotes a strict `value` to a [[CancelableFuture]] that's already complete. */
  def successful[T](value: T): CancelableFuture[T] =
    new Now[T](Success(value))

  /** Promotes a strict `Throwable` to a [[CancelableFuture]] that's already complete. */
  def failed[T](ex: Throwable): CancelableFuture[T] =
    new Now[T](Failure(ex))

  /** Promotes a strict `Try[T]` to a [[CancelableFuture]] that's already complete. */
  def fromTry[T](value: Try[T]): CancelableFuture[T] =
    value match {
      case Success(v) => successful(v)
      case Failure(ex) => failed(ex)
    }

  /** Internal; wraps any cancelable future `ref` into an [[Implementation]] */
  private def wrap[T](ref: CancelableFuture[T]): Implementation[T] =
    ref match {
      case _: Implementation[_] => ref.asInstanceOf[Implementation[T]]
      case _ => new Implementation[T](ref, ref)
    }

  /** An internal [[CancelableFuture]] implementation. */
  private final class Now[+T](immediate: Try[T]) extends CancelableFuture[T] {
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    def result(atMost: Duration)(implicit permit: CanAwait): T = immediate.get

    def cancel(): Unit = ()
    def isCompleted: Boolean = true
    val value: Some[Try[T]] = Some(immediate)

    def onComplete[U](f: (Try[T]) => U)(implicit executor: ExecutionContext): Unit =
      executor.execute(new Runnable { def run(): Unit = f(immediate) })

    // Overriding methods for getting CancelableFuture in return
    override def failed: CancelableFuture[Throwable] =
      immediate match {
        case Success(_) => new Now(Failure(new NoSuchElementException("failed")))
        case Failure(ex) => new Now(Success(ex))
      }
  }

  /** An actual [[CancelableFuture]] implementation; internal. */
  private final class Implementation[+T](underlying: Future[T], cancelable: Cancelable)
    extends CancelableFuture[T] {

    override def onComplete[U](f: (Try[T]) => U)(implicit executor: ExecutionContext): Unit =
      underlying.onComplete(f)(executor)
    override def isCompleted: Boolean =
      underlying.isCompleted
    override def value: Option[Try[T]] =
      underlying.value

    @throws[Exception](classOf[Exception])
    def result(atMost: Duration)(implicit permit: CanAwait): T =
      underlying.result(atMost)(permit)

    @throws[InterruptedException](classOf[InterruptedException])
    @throws[TimeoutException](classOf[TimeoutException])
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
      underlying.ready(atMost)(permit)
      this
    }

    override def cancel(): Unit =
      cancelable.cancel()

    // Overriding methods for getting CancelableFuture in return
    override def failed: CancelableFuture[Throwable] =
      new Implementation(underlying.failed, cancelable)
    override def transform[S](s: (T) => S, f: (Throwable) => Throwable)(implicit executor: ExecutionContext): CancelableFuture[S] =
      new Implementation(underlying.transform(s, f), cancelable)
    override def map[S](f: (T) => S)(implicit executor: ExecutionContext): CancelableFuture[S] =
      new Implementation(underlying.map(f), cancelable)
    override def flatMap[S](f: (T) => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
      new Implementation(underlying.flatMap(f), cancelable)
    override def filter(p: (T) => Boolean)(implicit executor: ExecutionContext): CancelableFuture[T] =
      new Implementation(underlying.filter(p), cancelable)
    override def collect[S](pf: PartialFunction[T, S])(implicit executor: ExecutionContext): CancelableFuture[S] =
      new Implementation(underlying.collect(pf), cancelable)
    override def recover[U >: T](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): CancelableFuture[U] =
      new Implementation(underlying.recover(pf), cancelable)
    override def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): CancelableFuture[U] =
      new Implementation(underlying.recoverWith(pf), cancelable)
    override def zip[U](that: Future[U]): CancelableFuture[(T, U)] =
      new Implementation(underlying.zip(that), cancelable)
    override def fallbackTo[U >: T](that: Future[U]): CancelableFuture[U] =
      new Implementation(underlying.fallbackTo(that), cancelable)
    override def mapTo[S](implicit tag: ClassTag[S]): CancelableFuture[S] =
      new Implementation(underlying.mapTo[S], cancelable)
    override def andThen[U](pf: PartialFunction[Try[T], U])(implicit executor: ExecutionContext): CancelableFuture[T] =
      new Implementation(underlying.andThen(pf), cancelable)
  }
}
