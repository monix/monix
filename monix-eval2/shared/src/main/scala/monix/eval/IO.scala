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

package monix.eval

import monix.eval.internal.IOFiber
import monix.execution.{ Callback, Cancelable, CancelableFuture, Scheduler }

import scala.concurrent.Promise
import scala.util.control.NonFatal

sealed abstract class IO[+A] {
  private[eval] def tag: Byte
  private[eval] def accept[AA >: A, R](visitor: IO.Visitor[AA, R]): R

  def flatMap[B](f: A => IO[B]): IO[B] =
    IO.FlatMap(this, f)

  def map[B](f: A => B): IO[B] =
    flatMap(a => IO.pure(f(a)))

  def unsafeRunAsync(cb: Callback[Throwable, A])(implicit s: Scheduler): CancelToken[IO] = {
    val fiber = new IOFiber[A](this, Callback.safe(cb), null, null)(s)
    s.execute(fiber)
    fiber.cancel
  }

  def unsafeRunAndForget()(implicit s: Scheduler): Unit =
    unsafeRunAsync(Callback.empty): Unit

  def unsafeRunToFuture()(implicit s: Scheduler): CancelableFuture[A] = {
    val p = Promise[A]()
    val cancel = unsafeRunAsync(Callback.fromPromise(p))
    CancelableFuture(p.future, Cancelable(cancel.unsafeRunAndForget _))
  }
}

object IO {

  def pure[A](a: A): IO[A] = Pure(a)

  def delay[A](thunk: => A): IO[A] =
    FlatMap[Unit, A](
      Pure(()),
      _ => {
        try
          Pure(thunk)
        catch {
          case NonFatal(e) =>
            RaiseError(e)
        }
      }
    )

  def defer[A](thunk: => IO[A]): IO[A] =
    delay(thunk).flatMap(identity)

  def raiseError[A](e: Throwable): IO[A] =
    RaiseError(e)

  def async[A](register: Callback[Throwable, A] => Unit): IO[A] =
    AsyncSimple((sc, cb) => register(Callback.safe(cb)(sc)))

  def async0[A](register: (Scheduler, Callback[Throwable, A]) => Unit): IO[A] =
    AsyncSimple((sc, cb) => register(sc, Callback.safe(cb)(sc)))

  def cont[S, A](cont: (Callback[Throwable, S], IO[S]) => IO[A]): IO[A] =
    IO.AsyncCont[S, A]((_, cb, get) => cont(cb, get))

  def cont0[S, A](cont: (Scheduler, Callback[Throwable, S], IO[S]) => IO[A]): IO[A] =
    IO.AsyncCont(cont)

  private[eval] final case class Pure[+A](a: A) extends IO[A] {
    def tag: Byte = 0
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] final case class RaiseError(e: Throwable) extends IO[Nothing] {
    def tag: Byte = 1
    def accept[AA >: Nothing, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] final case class FlatMap[A, +B](source: IO[A], f: A => IO[B]) extends IO[B] {
    def tag: Byte = 2
    def accept[BB >: B, R](visitor: Visitor[BB, R]): R = visitor.visit(this)
  }

  private[eval] final case class AsyncSimple[+A](
    start: (Scheduler, Callback[Throwable, A]) => Unit,
    // TODO: check the need for these defaults
    boundaryBefore: AsyncSimple.BoundaryPolicy = AsyncSimple.AsyncShifted,
    boundaryAfter: AsyncSimple.BoundaryPolicy = AsyncSimple.AsyncTrampolined,
  ) extends IO[A] {
    def tag: Byte = 3
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object AsyncSimple {
    type BoundaryPolicy = Byte
    final val AsyncTrampolined: BoundaryPolicy = 0.toByte
    final val AsyncShifted: BoundaryPolicy = 1.toByte
    final val Synchronous: BoundaryPolicy = 2.toByte
  }

  private[eval] final case class AsyncCont[A, +B](
    cont: (Scheduler, Callback[Throwable, A], IO[A]) => IO[B]
  ) extends IO[B] {
    def tag: Byte = 4
    def accept[BB >: B, R](visitor: Visitor[BB, R]): R = visitor.visit(this)
  }

  private[eval] trait Visitor[A, +R] {
    def visit(ref: Pure[A]): R
    def visit(ref: RaiseError): R
    def visit[S](ref: FlatMap[S, A]): R
    def visit[S](ref: AsyncCont[S, A]): R
    def visit(ref: AsyncSimple[A]): R
  }
}
