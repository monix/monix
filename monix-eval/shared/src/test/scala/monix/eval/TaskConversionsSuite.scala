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

import cats.effect._
import cats.laws._
import cats.laws.discipline._
import cats.syntax.all._
import cats.{Eval, effect}
import monix.execution.exceptions.DummyException

import scala.concurrent.duration._
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
    val io = Task.raiseError[Int](dummy).executeAsync.toIO
    val f = io.unsafeToFuture()

    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromConcurrent(task.toConcurrent[IO]) == task") { implicit s =>
    implicit val cs = s.contextShift[IO]
    check1 { (task: Task[Int]) =>
      Task.fromConcurrentEffect(task.toConcurrent[IO]) <-> task
    }
  }

  test("Task.fromAsync(task.toAsync[IO]) == task") { implicit s =>
    check1 { (task: Task[Int]) =>
      Task.fromEffect(task.toAsync[IO]) <-> task
    }
  }

  test("Task.fromConcurrent(task) == task") { implicit s =>
    val ref = Task.evalAsync(1)
    assertEquals(Task.fromConcurrentEffect(ref), ref)
  }

  test("Task.fromConcurrent(io)") { implicit s =>
    implicit val cs = s.contextShift[IO]

    val f = Task.fromConcurrentEffect(IO(1)).runAsync
    assertEquals(f.value, Some(Success(1)))

    val io2 = for (_ <- IO.shift; a <- IO(1)) yield a
    val f2 = Task.fromConcurrentEffect(io2).runAsync
    assertEquals(f2.value, None); s.tick()
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.fromAsync(Effect)") { implicit s =>
    implicit val cs = s.contextShift[IO]
    implicit val ioEffect: Effect[CIO] = new CustomEffect

    val f = Task.fromEffect(CIO(IO(1))).runAsync
    assertEquals(f.value, Some(Success(1)))

    val io2 = for (_ <- CIO(IO.shift); a <- CIO(IO(1))) yield a
    val f2 = Task.fromEffect(io2).runAsync
    assertEquals(f2.value, None); s.tick()
    assertEquals(f2.value, Some(Success(1)))

    val dummy = DummyException("dummy")
    val f3 = Task.fromEffect(CIO(IO.raiseError(dummy))).runAsync
    assertEquals(f3.value, Some(Failure(dummy)))
  }

  test("Task.fromConcurrent(ConcurrentEffect)") { implicit s =>
    implicit val cs = s.contextShift[IO]
    implicit val ioEffect: ConcurrentEffect[CIO] = new CustomConcurrentEffect()

    val f = Task.fromConcurrentEffect(CIO(IO(1))).runAsync
    assertEquals(f.value, Some(Success(1)))

    val io2 = for (_ <- CIO(IO.shift); a <- CIO(IO(1))) yield a
    val f2 = Task.fromConcurrentEffect(io2).runAsync
    assertEquals(f2.value, None); s.tick()
    assertEquals(f2.value, Some(Success(1)))

    val dummy = DummyException("dummy")
    val f3 = Task.fromConcurrentEffect(CIO(IO.raiseError(dummy))).runAsync
    assertEquals(f3.value, Some(Failure(dummy)))
  }

  test("Task.fromAsync(broken Effect)") { implicit s =>
    val dummy = DummyException("dummy")
    implicit val ioEffect: Effect[CIO] =
      new CustomEffect()(IO.contextShift(s)) {
        override def runAsync[A](fa: CIO[A])(cb: (Either[Throwable, A]) => IO[Unit]): SyncIO[Unit] =
          throw dummy
      }

    val f = Task.fromEffect(CIO(IO(1))).runAsync
    s.tick()

    assertEquals(f.value, None)
    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Task.fromConcurrent(broken ConcurrentEffect)") { implicit s =>
    val dummy = DummyException("dummy")
    implicit val ioEffect: ConcurrentEffect[CIO] =
      new CustomConcurrentEffect()(IO.contextShift(s)) {
        override def runCancelable[A](fa: CIO[A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[CancelToken[CIO]] =
          throw dummy
      }

    val f = Task.fromConcurrentEffect(CIO(IO(1))).runAsync
    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, None)

    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Task.fromIO is cancelable") { implicit s =>
    val timer = s.timer[IO]
    val io = timer.sleep(10.seconds)
    val f = Task.fromIO(io).runAsync

    s.tick()
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(f.value, None)

    f.cancel()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(f.value, None)

    s.tick(10.seconds)
    assertEquals(f.value, None)
  }

  test("Task.fromConcurrent(io) is cancelable") { implicit s =>
    implicit val cs = s.contextShift[IO]

    val timer = s.timer[IO]
    val io = timer.sleep(10.seconds)
    val f = Task.fromConcurrentEffect(io).runAsync

    s.tick()
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(f.value, None)

    f.cancel()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(f.value, None)

    s.tick(10.seconds)
    assertEquals(f.value, None)
  }

  test("Task.fromConcurrent(ConcurrentEffect) is cancelable") { implicit s =>
    implicit val cs = s.contextShift[IO]
    implicit val effect: ConcurrentEffect[CIO] = new CustomConcurrentEffect

    val timer = s.timer[CIO]
    val io = timer.sleep(10.seconds)
    val f = Task.fromConcurrentEffect(io)(effect).runAsync

    s.tick()
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(f.value, None)

    f.cancel()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(f.value, None)

    s.tick(10.seconds)
    assertEquals(f.value, None)
  }

  test("Task.fromConcurrent(task.to[IO]) preserves cancelability") { implicit s =>
    implicit val cs = s.contextShift[IO]

    val task0 = Task(1).delayExecution(10.seconds)
    val task = Task.fromConcurrentEffect(task0.toConcurrent[IO])

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, None)

    f.cancel()
    s.tick()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")

    s.tick(10.seconds)
    assertEquals(f.value, None)
  }

  test("Task.fromConcurrent(task.to[CIO]) preserves cancelability") { implicit s =>
    implicit val cs = s.contextShift[IO]
    implicit val effect: ConcurrentEffect[CIO] = new CustomConcurrentEffect

    val task0 = Task(1).delayExecution(10.seconds)
    val task = Task.fromConcurrentEffect(task0.toConcurrent[CIO])

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, None)

    f.cancel()
    s.tick()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")

    s.tick(10.seconds)
    assertEquals(f.value, None)
  }

  test("Task.fromAsync(task.to[IO]) preserves cancelability (because IO is known)") { implicit s =>
    implicit val cs = s.contextShift[IO]

    val task0 = Task(1).delayExecution(10.seconds)
    val task = Task.fromEffect(task0.toConcurrent[IO])

    val f = task.runAsync
    s.tick()
    assertEquals(f.value, None)

    f.cancel()
    s.tick()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")

    s.tick(10.seconds)
    assertEquals(f.value, None)
  }

  test("Task.fromConcurrent(task.toConcurrent[F]) <-> task (Effect)") { implicit s =>
    implicit val cs = s.contextShift[IO]
    implicit val effect = new CustomConcurrentEffect

    check1 { (task: Task[Int]) =>
      Task.fromConcurrentEffect(task.toConcurrent[CIO]) <-> task
    }
  }

  test("Task.fromAsync(task.toAsync[F]) <-> task") { implicit s =>
    implicit val cs = s.contextShift[IO]
    implicit val effect = new CustomEffect

    check1 { (task: Task[Int]) =>
      Task.fromEffect(task.toAsync[CIO]) <-> task
    }
  }

  test("Task.fromConcurrent(task.to[F]) <-> task (ConcurrentEffect)") { implicit s =>
    implicit val cs = s.contextShift[IO]
    implicit val effect = new CustomConcurrentEffect

    check1 { (task: Task[Int]) =>
      Task.fromConcurrentEffect(task.toConcurrent[CIO]) <-> task
    }
  }

  test("Task.fromEval") { implicit s =>
    var effect = 0
    val task = Task.fromEval(Eval.always { effect += 1; effect })

    assertEquals(task.runAsync.value, Some(Success(1)))
    assertEquals(task.runAsync.value, Some(Success(2)))
    assertEquals(task.runAsync.value, Some(Success(3)))
  }

  test("Task.fromEval protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fromEval(Eval.always { throw dummy })
    assertEquals(task.runAsync.value, Some(Failure(dummy)))
  }

  final case class CIO[+A](io: IO[A])

  class CustomEffect(implicit cs: ContextShift[IO]) extends Effect[CIO] {
    override def runAsync[A](fa: CIO[A])(cb: (Either[Throwable, A]) => IO[Unit]): SyncIO[Unit] =
      fa.io.runAsync(cb)
    override def async[A](k: ((Either[Throwable, A]) => Unit) => Unit): CIO[A] =
      CIO(IO.async(k))
    override def asyncF[A](k: ((Either[Throwable, A]) => Unit) => CIO[Unit]): CIO[A] =
      CIO(IO.asyncF(cb => k(cb).io))
    override def suspend[A](thunk: => CIO[A]): CIO[A] =
      CIO(IO.suspend(thunk.io))
    override def flatMap[A, B](fa: CIO[A])(f: (A) => CIO[B]): CIO[B] =
      CIO(fa.io.flatMap(a => f(a).io))
    override def tailRecM[A, B](a: A)(f: (A) => CIO[Either[A, B]]): CIO[B] =
      CIO(IO.ioConcurrentEffect.tailRecM(a)(x => f(x).io))
    override def raiseError[A](e: Throwable): CIO[A] =
      CIO(IO.raiseError(e))
    override def handleErrorWith[A](fa: CIO[A])(f: (Throwable) => CIO[A]): CIO[A] =
      CIO(IO.ioConcurrentEffect.handleErrorWith(fa.io)(x => f(x).io))
    override def pure[A](x: A): CIO[A] =
      CIO(IO.pure(x))
    override def liftIO[A](ioa: IO[A]): CIO[A] =
      CIO(ioa)
    override def bracketCase[A, B](acquire: CIO[A])(use: A => CIO[B])(release: (A, ExitCase[Throwable]) => CIO[Unit]): CIO[B] =
      CIO(acquire.io.bracketCase(a => use(a).io)((a, e) => release(a, e).io))
  }

  class CustomConcurrentEffect(implicit cs: ContextShift[IO])
    extends CustomEffect with ConcurrentEffect[CIO] {

    override def runCancelable[A](fa: CIO[A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[CancelToken[CIO]] =
      fa.io.runCancelable(cb).map(CIO(_))
    override def cancelable[A](k: (Either[Throwable, A] => Unit) => CancelToken[CIO]): CIO[A] =
      CIO(IO.cancelable(cb => k(cb).io))
    override def uncancelable[A](fa: CIO[A]): CIO[A] =
      CIO(fa.io.uncancelable)
    override def start[A](fa: CIO[A]): CIO[effect.Fiber[CIO, A]] =
      CIO(fa.io.start.map(fiberT))
    override def racePair[A, B](fa: CIO[A], fb: CIO[B]) =
      CIO(IO.racePair(fa.io, fb.io).map {
        case Left((a, fiber)) => Left((a, fiberT(fiber)))
        case Right((fiber, b)) => Right((fiberT(fiber), b))
      })
    private def fiberT[A](fiber: effect.Fiber[IO, A]): effect.Fiber[CIO, A] =
      effect.Fiber(CIO(fiber.join), CIO(fiber.cancel))
  }
}
