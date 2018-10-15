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

package monix.catnap

import cats.effect.{Async, Concurrent}
import monix.execution.CancelableFuture
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * A type class for conversions from [[scala.concurrent.Future]].
  */
trait LiftFuture[F[_]] {
  def liftFuture[A](fa: F[Future[A]]): F[A]
}

object LiftFuture {
  /**
    * Accessor for [[LiftFuture]] values that are in scope.
    * {{{
    *   import cats.effect.IO
    *   import scala.concurrent.Future
    *
    *   val ioa = LiftFuture[IO].liftFuture(IO(Future.successful(1)))
    * }}}
    */
  def apply[F[_]](implicit F: LiftFuture[F]): LiftFuture[F] = F

  /**
    * Utility for converting [[scala.concurrent.Future Future]] values into
    * data types that implement
    * [[https://typelevel.org/cats-effect/typeclasses/async.html cats.effect.Async]].
    *
    * N.B. the implementation discriminates
    * [[monix.execution.CancelableFuture CancelableFuture]] via sub-typing,
    * and if the given future is cancelable, then the resulting instance
    * is also cancelable.
    */
  def toAsync[F[_], A](fa: F[Future[A]])(implicit F: Async[F]): F[A] =
    F.flatMap(fa) { future =>
      future.value match {
        case Some(value) => F.fromTry(value)
        case _ => startAsync(future)
      }
    }

  /**
    * Utility for converting [[scala.concurrent.Future Future]] values into
    * data types that implement
    * [[https://typelevel.org/cats-effect/typeclasses/concurrent.html cats.effect.Concurrent]].
    *
    * N.B. the implementation discriminates
    * [[monix.execution.CancelableFuture CancelableFuture]] via sub-typing,
    * and if the given future is cancelable, then the resulting instance
    * is also cancelable.
    */
  def toConcurrent[F[_], A](fa: F[Future[A]])(implicit F: Concurrent[F]): F[A] =
    F.flatMap(fa) { future =>
      future.value match {
        case Some(value) => F.fromTry(value)
        case _ =>
          future match {
            case cf: CancelableFuture[A] @unchecked =>
              startCancelable(cf)
            case _ =>
              startAsync(future)
          }
      }
    }

  /**
    * Implicit instance of [[LiftFuture]] for all `Concurrent` or `Async`
    * data types.
    */
  implicit def forConcurrentOrAsync[F[_]](implicit F: Concurrent[F] OrElse Async[F]): LiftFuture[F] =
    F.unify match {
      case ref: Concurrent[F] @unchecked =>
        new LiftFuture[F] { def liftFuture[A](fa: F[Future[A]]): F[A] = toConcurrent(fa)(ref) }
      case ref =>
        new LiftFuture[F] { def liftFuture[A](fa: F[Future[A]]): F[A] = toAsync(fa)(ref) }
    }

  /**
    * Provides extension methods when imported in scope via [[syntax]].
    *
    * {{{
    *   import monix.catnap.syntax._
    * }}}
    */
  trait Syntax[F[_], FutureLike[T] <: Future[T], A] extends Any {
    def source: F[FutureLike[A]]

    def liftFuture(implicit F: Concurrent[F] OrElse Async[F]): F[A] =
      F.unify match {
        case ref: Concurrent[F] @unchecked =>
          toConcurrent(source.asInstanceOf[F[Future[A]]])(ref)
        case ref =>
          toAsync(source.asInstanceOf[F[Future[A]]])(ref)
      }
  }

  private def start[A](fa: Future[A], cb: Either[Throwable, A] => Unit): Unit = {
    implicit val ec = immediate
    fa.onComplete(r => cb(r match {
      case Success(a) => Right(a)
      case Failure(e) => Left(e)
    }))(immediate)
  }

  private def startAsync[F[_], A](fa: Future[A])(implicit F: Async[F]): F[A] =
    F.async { cb => start(fa, cb) }

  private def startCancelable[F[_], A](fa: CancelableFuture[A])(implicit F: Concurrent[F]): F[A] =
    F.cancelable { cb =>
      start(fa, cb)
      fa.toCancelToken[F]
    }
}
