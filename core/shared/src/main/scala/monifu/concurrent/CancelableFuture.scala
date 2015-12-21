/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: https://monifu.org
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

package monifu.concurrent

import monifu.concurrent.cancelables.SingleAssignmentCancelable
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.Try

/**
 * Represents an asynchronous computation that can be canceled
 * as long as it isn't complete.
 */
final class CancelableFuture[+T] private[concurrent] (
  private[concurrent] val underlying: Future[T],
  private[concurrent] val cancelable: Cancelable)
  extends Future[T] with Cancelable {

  def onComplete[U](f: (Try[T]) => U)(implicit executor: ExecutionContext): Unit =
    underlying.onComplete(f)(executor)
  def isCompleted: Boolean =
    underlying.isCompleted
  def value: Option[Try[T]] =
    underlying.value

  @throws[Exception](classOf[Exception])
  def result(atMost: Duration)(implicit permit: CanAwait): T =
    underlying.result(atMost)(permit)

  @throws[InterruptedException](classOf[InterruptedException])
  @throws[TimeoutException](classOf[TimeoutException])
  def ready(atMost: Duration)(implicit permit: CanAwait): CancelableFuture.this.type = {
    underlying.ready(atMost)(permit)
    this
  }

  def cancel(): Boolean =
    cancelable.cancel()

  // Overriding methods for getting CancelableFuture in return

  override def failed: CancelableFuture[Throwable] =
    new CancelableFuture(underlying.failed, cancelable)
  override def transform[S](s: (T) => S, f: (Throwable) => Throwable)(implicit executor: ExecutionContext): CancelableFuture[S] =
    new CancelableFuture(underlying.transform(s, f), cancelable)
  override def map[S](f: (T) => S)(implicit executor: ExecutionContext): CancelableFuture[S] =
    new CancelableFuture(underlying.map(f), cancelable)
  override def flatMap[S](f: (T) => Future[S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    new CancelableFuture(underlying.flatMap(f), cancelable)
  override def filter(p: (T) => Boolean)(implicit executor: ExecutionContext): CancelableFuture[T] =
    new CancelableFuture(underlying.filter(p), cancelable)
  override def collect[S](pf: PartialFunction[T, S])(implicit executor: ExecutionContext): CancelableFuture[S] =
    new CancelableFuture(underlying.collect(pf), cancelable)
  override def recover[U >: T](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): CancelableFuture[U] =
    new CancelableFuture(underlying.recover(pf), cancelable)
  override def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): CancelableFuture[U] =
    new CancelableFuture(underlying.recoverWith(pf), cancelable)
  override def zip[U](that: Future[U]): CancelableFuture[(T, U)] =
    new CancelableFuture(underlying.zip(that), cancelable)
  override def fallbackTo[U >: T](that: Future[U]): CancelableFuture[U] =
    new CancelableFuture(underlying.fallbackTo(that), cancelable)
  override def mapTo[S](implicit tag: ClassTag[S]): CancelableFuture[S] =
    new CancelableFuture(underlying.mapTo[S], cancelable)
  override def andThen[U](pf: PartialFunction[Try[T], U])(implicit executor: ExecutionContext): CancelableFuture[T] =
    new CancelableFuture(underlying.andThen(pf), cancelable)
}

object CancelableFuture {
  /**
   * Builder for a [[CancelableFuture]] given a [[Task]].
   */
  def apply[T](task: Task[T])(implicit s: Scheduler): CancelableFuture[T] = {
    val p = Promise[T]()
    val c = SingleAssignmentCancelable()

    val cancelable = Cancelable {
      if (c.cancel())
        p.tryFailure(new CancellationException)
    }

    c := task.unsafeRun(new TaskCallback[T] {
      val scheduler = s
      def onError(ex: Throwable): Unit =
        if (c.cancel()) p.tryFailure(ex)
      def onSuccess(value: T): Unit =
        if (c.cancel()) p.trySuccess(value)
    })

    new CancelableFuture(p.future, cancelable)
  }
}