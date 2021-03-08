/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import cats.Applicative
import cats.effect.kernel.{Cont, Deferred, Fiber, Poll, Ref}
import cats.effect.{Async, IO, Sync}

object Overrides {
  implicit val syncIO: Sync[IO] = CustomAsyncIO
  implicit val asyncIO: Async[IO] = CustomAsyncIO

  private object CustomAsyncIO extends Async[IO] {
    private[this] val base = IO.asyncForIO

    override def evalOn[A](fa: IO[A], ec: ExecutionContext): IO[A] = base.evalOn(fa, ec)

    override def executionContext: IO[ExecutionContext] = base.executionContext

    override def cont[K, R](body: Cont[IO, K, R]): IO[R] = base.cont(body)

    override def suspend[A](hint: Sync.Type)(thunk: => A): IO[A] = base.suspend(hint)(thunk)

    override def sleep(time: FiniteDuration): IO[Unit] = base.sleep(time)

    override def ref[A](a: A): IO[Ref[IO, A]] = base.ref(a)

    override def deferred[A]: IO[Deferred[IO, A]] = base.deferred

    override def start[A](fa: IO[A]): IO[Fiber[IO, Throwable, A]] = base.start(fa)

    override def cede: IO[Unit] = base.cede

    override def forceR[A, B](fa: IO[A])(fb: IO[B]): IO[B] = base.forceR(fa)(fb)

    override def uncancelable[A](body: Poll[IO] => IO[A]): IO[A] = base.uncancelable(body)

    override def canceled: IO[Unit] = base.canceled

    override def onCancel[A](fa: IO[A], fin: IO[Unit]): IO[A] = base.onCancel(fa, fin)

    override def raiseError[A](e: Throwable): IO[A] = base.raiseError(e)

    override def handleErrorWith[A](fa: IO[A])(f: Throwable => IO[A]): IO[A] = base.handleErrorWith(fa)(f)

    override def pure[A](x: A): IO[A] = base.pure(x)

    override def applicative: Applicative[IO] = base

    override def monotonic: IO[FiniteDuration] = base.monotonic

    override def realTime: IO[FiniteDuration] = base.realTime

    override def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = base.flatMap(fa)(f)

    override def tailRecM[A, B](a: A)(f: A => IO[Either[A, B]]): IO[B] = base.tailRecM(a)(f)
  }
}
