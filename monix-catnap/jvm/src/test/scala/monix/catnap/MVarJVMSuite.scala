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

package monix.catnap

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.implicits._
import monix.execution.schedulers.SchedulerService
import monix.execution.{ Scheduler, TestSuite, TestUtils }
import munit.{ FunSuite, Location }

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.CancellationException
import scala.concurrent.duration._

class MVarEmptyJVMParallelism1Suite extends BaseMVarJVMSuite(1) {
  def allocateConcurrent(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.empty[IO, Unit]()(OrElse.primary(implicitly[Concurrent[IO]]), cs)
  def allocateAsync(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.empty[IO, Unit]()(OrElse.secondary(IO.ioEffect), cs)
  def acquire(ref: MVar[IO, Unit]): IO[Unit] =
    ref.take
  def release(ref: MVar[IO, Unit]): IO[Unit] =
    ref.put(())
}

class MVarEmptyJVMParallelism2Suite extends BaseMVarJVMSuite(2) {
  def allocateConcurrent(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.empty[IO, Unit]()(OrElse.primary(implicitly[Concurrent[IO]]), cs)
  def allocateAsync(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.empty[IO, Unit]()(OrElse.secondary(IO.ioEffect), cs)
  def acquire(ref: MVar[IO, Unit]): IO[Unit] =
    ref.take
  def release(ref: MVar[IO, Unit]): IO[Unit] =
    ref.put(())
}

class MVarEmptyJVMParallelism4Suite extends BaseMVarJVMSuite(4) {
  def allocateConcurrent(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.empty[IO, Unit]()(OrElse.primary(implicitly[Concurrent[IO]]), cs)
  def allocateAsync(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.empty[IO, Unit]()(OrElse.secondary(IO.ioEffect), cs)
  def acquire(ref: MVar[IO, Unit]): IO[Unit] =
    ref.take
  def release(ref: MVar[IO, Unit]): IO[Unit] =
    ref.put(())
}

// -----------------------------------------------------------------

class MVarFullJVMParallelism1Suite extends BaseMVarJVMSuite(1) {
  def allocateConcurrent(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.of[IO, Unit](())(OrElse.primary(implicitly[Concurrent[IO]]), cs)
  def allocateAsync(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.of[IO, Unit](())(OrElse.secondary(IO.ioEffect), cs)
  def acquire(ref: MVar[IO, Unit]): IO[Unit] =
    ref.put(())
  def release(ref: MVar[IO, Unit]): IO[Unit] =
    ref.take
}

class MVarFullJVMParallelism2Suite extends BaseMVarJVMSuite(2) {
  def allocateConcurrent(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.of[IO, Unit](())(OrElse.primary(implicitly[Concurrent[IO]]), cs)
  def allocateAsync(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.of[IO, Unit](())(OrElse.secondary(IO.ioEffect), cs)
  def acquire(ref: MVar[IO, Unit]): IO[Unit] =
    ref.put(())
  def release(ref: MVar[IO, Unit]): IO[Unit] =
    ref.take
}

class MVarFullJVMParallelism4Suite extends BaseMVarJVMSuite(4) {
  def allocateConcurrent(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.of[IO, Unit](())(OrElse.primary(implicitly[Concurrent[IO]]), cs)
  def allocateAsync(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]] =
    MVar.of[IO, Unit](())(OrElse.secondary(IO.ioEffect), cs)
  def acquire(ref: MVar[IO, Unit]): IO[Unit] =
    ref.put(())
  def release(ref: MVar[IO, Unit]): IO[Unit] =
    ref.take
}

// -----------------------------------------------------------------

abstract class BaseMVarJVMSuite(parallelism: Int) extends TestSuite[SchedulerService] with TestUtils {
  def setup(): SchedulerService =
    Scheduler.computation(
      name = s"mvar-suite-par-$parallelism",
      parallelism = parallelism
    )

  def tearDown(env: SchedulerService): Unit = {
    env.shutdown()
    assert(env.awaitTermination(30.seconds), "env.awaitTermination")
  }

  implicit def contextShift(implicit ec: Scheduler): ContextShift[IO] =
    SchedulerEffect.contextShift[IO](ec)(IO.ioEffect)
  implicit def timer(implicit ec: Scheduler): Timer[IO] =
    SchedulerEffect.timerLiftIO[IO](ec)(IO.ioEffect)

  // ----------------------------------------------------------------------------

  val iterations = if (isCI) 1000 else 10000
  val timeout = if (isCI) 30.seconds else 10.seconds

  def allocateConcurrent(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]]
  def allocateAsync(implicit cs: ContextShift[IO]): IO[MVar[IO, Unit]]
  def acquire(ref: MVar[IO, Unit]): IO[Unit]
  def release(ref: MVar[IO, Unit]): IO[Unit]

  // ----------------------------------------------------------------------------

  fixture.test("MVar (concurrent) — issue #380: producer keeps its thread, consumer stays forked") { implicit ec =>
    for (_ <- 0 until iterations) {
      val name = Thread.currentThread().getName

      def get(df: MVar[IO, Unit]) =
        for {
          _ <- IO(assert(Thread.currentThread().getName != name))
          _ <- acquire(df)
          _ <- IO(assert(Thread.currentThread().getName != name))
        } yield ()

      val task = for {
        df <- allocateConcurrent
        fb <- get(df).start
        _  <- IO(assertEquals(Thread.currentThread().getName, name))
        _  <- release(df)
        _  <- IO(assertEquals(Thread.currentThread().getName, name))
        _  <- fb.join
      } yield ()

      assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  fixture.test("MVar (concurrent) — issue #380: with foreverM; with latch") { implicit ec =>
    for (_ <- 0 until iterations) {
      val cancelLoop = new AtomicBoolean(false)
      val unit = IO {
        if (cancelLoop.get()) throw new CancellationException
      }

      try {
        val task = for {
          df    <- allocateConcurrent
          latch <- Deferred.uncancelable[IO, Unit]
          fb    <- (latch.complete(()) *> acquire(df) *> unit.foreverM).start
          _     <- latch.get
          _     <- release(df).timeout(timeout).guarantee(fb.cancel)
        } yield ()

        assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      } finally {
        cancelLoop.set(true)
      }
    }
  }

  fixture.test("MVar (concurrent) — issue #380: with foreverM; without latch") { implicit ec =>
    for (_ <- 0 until iterations) {
      val cancelLoop = new AtomicBoolean(false)
      val unit = IO {
        if (cancelLoop.get()) throw new CancellationException
      }

      try {
        val task = for {
          df <- allocateConcurrent
          fb <- (acquire(df) *> unit.foreverM).start
          _  <- release(df).timeout(timeout).guarantee(fb.cancel)
        } yield ()

        assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      } finally {
        cancelLoop.set(true)
      }
    }
  }

  fixture.test("MVar (concurrent) — issue #380: with cooperative light async boundaries; with latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.async[Unit](cb => cb(Right(()))) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d     <- allocateConcurrent
        latch <- Deferred.uncancelable[IO, Unit]
        fb    <- (latch.complete(()) *> acquire(d) *> foreverAsync(0)).start
        _     <- latch.get
        _     <- release(d).timeout(5.seconds).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  fixture.test("MVar (concurrent) — issue #380: with cooperative light async boundaries; without latch") {
    implicit ec =>
      def run = {
        def foreverAsync(i: Int): IO[Unit] = {
          if (i == 512) IO.async[Unit](cb => cb(Right(()))) >> foreverAsync(0)
          else IO.unit >> foreverAsync(i + 1)
        }

        for {
          d  <- allocateConcurrent
          fb <- (acquire(d) *> foreverAsync(0)).start
          _  <- release(d).timeout(5.seconds).guarantee(fb.cancel)
        } yield true
      }

      for (_ <- 0 until iterations) {
        assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      }
  }

  fixture.test("MVar (concurrent) — issue #380: with cooperative full async boundaries; with latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.unit.start.flatMap(_.join) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d     <- allocateConcurrent
        latch <- Deferred.uncancelable[IO, Unit]
        fb    <- (latch.complete(()) *> acquire(d) *> foreverAsync(0)).start
        _     <- latch.get
        _     <- release(d).timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  fixture.test("MVar (concurrent) — issue #380: with cooperative full async boundaries; without latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.unit.start.flatMap(_.join) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d  <- allocateConcurrent
        fb <- (acquire(d) *> foreverAsync(0)).start
        _  <- release(d).timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  fixture.test("MVar (async) — issue #380: producer keeps its thread, consumer stays forked") { implicit ec =>
    for (_ <- 0 until iterations) {
      val name = Thread.currentThread().getName

      def get(df: MVar[IO, Unit]) =
        for {
          _ <- IO(assert(Thread.currentThread().getName != name))
          _ <- acquire(df)
          _ <- IO(assert(Thread.currentThread().getName != name))
        } yield ()

      val task = for {
        df <- allocateAsync
        fb <- get(df).start
        _  <- IO(assertEquals(Thread.currentThread().getName, name))
        _  <- release(df)
        _  <- IO(assertEquals(Thread.currentThread().getName, name))
        _  <- fb.join
      } yield ()

      assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  fixture.test("MVar (async) — issue #380: with foreverM; with latch") { implicit ec =>
    for (_ <- 0 until iterations) {
      val cancelLoop = new AtomicBoolean(false)
      val unit = IO {
        if (cancelLoop.get()) throw new CancellationException
      }

      try {
        val task = for {
          df    <- allocateAsync
          latch <- Deferred[IO, Unit]
          fb    <- (latch.complete(()) *> acquire(df) *> unit.foreverM).start
          _     <- latch.get
          _     <- release(df).timeout(timeout).guarantee(fb.cancel)
        } yield ()

        assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      } finally {
        cancelLoop.set(true)
      }
    }
  }

  fixture.test("MVar (async) — issue #380: with foreverM; without latch") { implicit ec =>
    for (_ <- 0 until iterations) {
      val cancelLoop = new AtomicBoolean(false)
      val unit = IO {
        if (cancelLoop.get()) throw new CancellationException
      }

      try {
        val task = for {
          df <- allocateAsync
          fb <- (acquire(df) *> unit.foreverM).start
          _  <- release(df).timeout(timeout).guarantee(fb.cancel)
        } yield ()

        assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      } finally {
        cancelLoop.set(true)
      }
    }
  }

  fixture.test("MVar (async) — issue #380: with cooperative light async boundaries; with latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.async[Unit](cb => cb(Right(()))) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d     <- allocateAsync
        latch <- Deferred.uncancelable[IO, Unit]
        fb    <- (latch.complete(()) *> acquire(d) *> foreverAsync(0)).start
        _     <- latch.get
        _     <- release(d).timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  fixture.test("MVar (async) — issue #380: with cooperative light async boundaries; without latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.async[Unit](cb => cb(Right(()))) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d  <- allocateAsync
        fb <- (acquire(d) *> foreverAsync(0)).start
        _  <- release(d).timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  fixture.test("MVar (async) — issue #380: with cooperative full async boundaries; with latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.unit.start.flatMap(_.join) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d     <- allocateAsync
        latch <- Deferred.uncancelable[IO, Unit]
        fb    <- (latch.complete(()) *> acquire(d) *> foreverAsync(0)).start
        _     <- latch.get
        _     <- release(d).timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }
}
