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
import cats.{ CoflatMap, Eval, Monad, MonadError, StackSafeMonad }
import monix.execution.CancelableFuture.Pure

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/** Implementation of Cats type classes for the
  * [[CancelableFuture]] data type.
  *
  * @param ec is the
  *        [[scala.concurrent.ExecutionContext ExecutionContext]]
  *        needed since future transformations rely on an
  *        `ExecutionContext` passed explicitly (by means of an
  *        implicit parameter) on each operation
  */
final class CancelableFutureCatsInstances(implicit ec: ExecutionContext)
  extends Monad[CancelableFuture] with StackSafeMonad[CancelableFuture] with CoflatMap[CancelableFuture]
  with MonadError[CancelableFuture, Throwable] {
  import CancelableFutureCatsInstances._

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
  override def recoverWith[A](fa: CancelableFuture[A])(
    pf: PartialFunction[Throwable, CancelableFuture[A]]
  ): CancelableFuture[A] =
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

object CancelableFutureCatsInstances {
  // Reusable reference to use in `CatsInstances.attempt`
  private val liftToEitherRef: (Try[Any] => CancelableFuture[Either[Throwable, Any]]) =
    tryA =>
      new Pure(Success(tryA match {
        case Success(a) => Right(a)
        case Failure(e) => Left(e)
      }))
}
