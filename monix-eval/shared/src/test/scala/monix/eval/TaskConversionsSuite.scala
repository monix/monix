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

package monix.eval

import cats.Eval
import cats.effect.{Effect, IO}
import cats.laws._
import cats.laws.discipline._
import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success}

object TaskConversionsSuite extends BaseTestSuite {
  test("Task.fromIO(task.toIO) == task") { implicit s =>
    check1 { (task: Task[Int]) =>
      Task.fromIO(task.toIO) <-> task
    }
  }

  test("Task.fromIO(IO.raiseError(e))") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fromIO(IO.raiseError(dummy))
    assertEquals(task.runAsync.value, Some(Failure(dummy)))
  }

  test("Task.fromIO(IO.raiseError(e).shift)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fromIO(for (_ <- IO.shift(s); x <- IO.raiseError[Int](dummy)) yield x)
    val f = task.runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.now(v).toIO") { implicit s =>
    assertEquals(Task.now(10).toIO.unsafeRunSync(), 10)
  }

  test("Task.raiseError(dummy).toIO") { implicit s =>
    val dummy = DummyException("dummy")
    intercept[DummyException] {
      Task.raiseError(dummy).toIO.unsafeRunSync()
    }
  }

  test("Task.eval(thunk).toIO") { implicit s =>
    assertEquals(Task.eval(10).toIO.unsafeRunSync(), 10)
  }

  test("Task.eval(fa).asyncBoundary.toIO") { implicit s =>
    val io = Task.eval(1).asyncBoundary.toIO
    val f = io.unsafeToFuture()

    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.raiseError(dummy).asyncBoundary.toIO") { implicit s =>
    val dummy = DummyException("dummy")
    val io = Task.fork(Task.raiseError[Int](dummy)).toIO
    val f = io.unsafeToFuture()

    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromEffect(task) == task") { implicit s =>
    val ref = Task(1)
    assertEquals(Task.fromEffect(ref), ref)
  }

  test("Task.fromEffect(io)") { implicit s =>
    val f = Task.fromEffect(IO(1)).runAsync
    assertEquals(f.value, Some(Success(1)))

    val io2 = for (_ <- IO.shift; a <- IO(1)) yield a
    val f2 = Task.fromEffect(io2).runAsync
    assertEquals(f2.value, None); s.tick()
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.fromEffect(io) with custom Effect") { implicit s =>
    implicit val ioEffect: Effect[IO] = new CustomIOEffect

    val f = Task.fromEffect(IO(1)).runAsync
    assertEquals(f.value, Some(Success(1)))

    val io2 = for (_ <- IO.shift; a <- IO(1)) yield a
    val f2 = Task.fromEffect(io2).runAsync
    assertEquals(f2.value, None); s.tick()
    assertEquals(f2.value, Some(Success(1)))

    val dummy = DummyException("dummy")
    val f3 = Task.fromEffect(IO.raiseError(dummy)).runAsync
    assertEquals(f3.value, Some(Failure(dummy)))
  }

  test("Task.fromEffect(io) with broken Effect") { implicit s =>
    val dummy = DummyException("dummy")
    implicit val ioEffect: Effect[IO] = new CustomIOEffect {
      override def runAsync[A](fa: IO[A])(cb: (Either[Throwable, A]) => IO[Unit]): IO[Unit] =
        throw dummy
    }

    val f = Task.fromEffect(IO(1)).runAsync
    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, None)

    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Task.fromEval") { implicit s =>
    var effect = 0
    val task = Task.fromEval(Eval.always { effect += 1; effect })

    assertEquals(task.runAsync.value, Some(Success(1)))
    assertEquals(task.runAsync.value, Some(Success(2)))
    assertEquals(task.runAsync.value, Some(Success(3)))
  }

  test("Task.fromEval protects against user error") { implicit s =>
    val dummy = new DummyException("dummy")
    val task = Task.fromEval(Eval.always { throw dummy })
    assertEquals(task.runAsync.value, Some(Failure(dummy)))
  }

  class CustomIOEffect extends Effect[IO] {
    def runAsync[A](fa: IO[A])(cb: (Either[Throwable, A]) => IO[Unit]): IO[Unit] =
      fa.runAsync(cb)
    def async[A](k: ((Either[Throwable, A]) => Unit) => Unit): IO[A] =
      IO.async(k)
    def suspend[A](thunk: => IO[A]): IO[A] =
      IO.suspend(thunk)
    def flatMap[A, B](fa: IO[A])(f: (A) => IO[B]): IO[B] =
      fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: (A) => IO[Either[A, B]]): IO[B] =
      IO.ioEffect.tailRecM(a)(f)
    def raiseError[A](e: Throwable): IO[A] =
      IO.raiseError(e)
    def handleErrorWith[A](fa: IO[A])(f: (Throwable) => IO[A]): IO[A] =
      IO.ioEffect.handleErrorWith(fa)(f)
    def pure[A](x: A): IO[A] =
      IO.pure(x)
    override def liftIO[A](ioa: IO[A]): IO[A] =
      ioa
  }
}
