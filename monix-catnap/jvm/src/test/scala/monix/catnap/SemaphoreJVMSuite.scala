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

package monix.catnap

import java.util.concurrent.atomic.AtomicBoolean

import cats.effect.concurrent.Deferred
import cats.effect.{ ContextShift, IO, Timer }
import cats.implicits._
import minitest.TestSuite
import monix.execution.{ Scheduler, TestUtils }
import monix.execution.schedulers.SchedulerService

import scala.concurrent.CancellationException
import scala.concurrent.duration._

object SemaphoreJVMParallelism1Tests extends BaseSemaphoreJVMTests(1)
object SemaphoreJVMParallelism2Tests extends BaseSemaphoreJVMTests(2)
object SemaphoreJVMParallelism4Tests extends BaseSemaphoreJVMTests(4)

abstract class BaseSemaphoreJVMTests(parallelism: Int) extends TestSuite[SchedulerService] with TestUtils {
  def setup(): SchedulerService =
    Scheduler.computation(
      name = s"semaphore-suite-par-$parallelism",
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

  test("Semaphore (concurrent) — issue #380: — producer keeps its thread, consumer stays forked") { implicit ec =>
    for (_ <- 0 until iterations) {
      val name = Thread.currentThread().getName

      def get(df: Semaphore[IO]) =
        for {
          _ <- IO(assert(Thread.currentThread().getName != name))
          _ <- df.acquire
          _ <- IO(assert(Thread.currentThread().getName != name))
        } yield ()

      val task = for {
        df <- Semaphore[IO](0)(OrElse.primary(IO.ioConcurrentEffect), contextShift)
        fb <- get(df).start
        _  <- IO(assertEquals(Thread.currentThread().getName, name))
        _  <- df.release
        _  <- IO(assertEquals(Thread.currentThread().getName, name))
        _  <- fb.join
      } yield ()

      val dt = 10.seconds
      assert(task.unsafeRunTimed(dt).nonEmpty, s"; timed-out after $dt")
    }
  }

  test("Semaphore (concurrent) — issue #380: with foreverM and latch") { implicit ec =>
    for (_ <- 0 until iterations) {
      val cancelLoop = new AtomicBoolean(false)
      val unit = IO {
        if (cancelLoop.get()) throw new CancellationException
      }

      try {
        val task = for {
          df    <- Semaphore[IO](0)(OrElse.primary(IO.ioConcurrentEffect), contextShift)
          latch <- Deferred[IO, Unit]
          fb    <- (latch.complete(()) *> df.acquire *> unit.foreverM).start
          _     <- latch.get
          _     <- df.release.timeout(timeout).guarantee(fb.cancel)
        } yield ()

        assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      } finally {
        cancelLoop.set(true)
      }
    }
  }

  test("Semaphore (concurrent) — issue #380: with foreverM and no latch") { implicit ec =>
    for (_ <- 0 until iterations) {
      val cancelLoop = new AtomicBoolean(false)
      val unit = IO {
        if (cancelLoop.get()) throw new CancellationException
      }

      try {
        val task = for {
          df <- Semaphore[IO](0)(OrElse.primary(IO.ioConcurrentEffect), contextShift)
          fb <- (df.acquire *> unit.foreverM).start
          _  <- df.release.timeout(timeout).guarantee(fb.cancel)
        } yield ()

        assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      } finally {
        cancelLoop.set(true)
      }
    }
  }

  test("Semaphore (concurrent) — issue #380: with cooperative light async boundaries; with latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.async[Unit](cb => cb(Right(()))) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d     <- Semaphore[IO](0)(OrElse.primary(IO.ioConcurrentEffect), contextShift)
        latch <- Deferred[IO, Unit]
        fb    <- (latch.complete(()) *> d.acquire *> foreverAsync(0)).start
        _     <- latch.get
        _     <- d.release.timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  test("Semaphore (concurrent) — issue #380: with cooperative light async boundaries; with no latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.async[Unit](cb => cb(Right(()))) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d  <- Semaphore[IO](0)(OrElse.primary(IO.ioConcurrentEffect), contextShift)
        fb <- (d.acquire *> foreverAsync(0)).start
        _  <- d.release.timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  test("Semaphore (concurrent) — issue #380: with cooperative full async boundaries; with latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.unit.start.flatMap(_.join) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d     <- Semaphore[IO](0)(OrElse.primary(IO.ioConcurrentEffect), contextShift)
        latch <- Deferred[IO, Unit]
        fb    <- (latch.complete(()) *> d.acquire *> foreverAsync(0)).start
        _     <- latch.get
        _     <- d.release.timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  test("Semaphore (concurrent) — issue #380: with cooperative full async boundaries; with no latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.unit.start.flatMap(_.join) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d  <- Semaphore[IO](0)(OrElse.primary(IO.ioConcurrentEffect), contextShift)
        fb <- (d.acquire *> foreverAsync(0)).start
        _  <- d.release.timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  test("Semaphore (async) — issue #380: producer keeps its thread, consumer stays forked") { implicit ec =>
    for (_ <- 0 until iterations) {
      val name = Thread.currentThread().getName

      def get(df: Semaphore[IO]) =
        for {
          _ <- IO(assert(Thread.currentThread().getName != name))
          _ <- df.acquire
          _ <- IO(assert(Thread.currentThread().getName != name))
        } yield ()

      val task = for {
        df <- Semaphore[IO](0)(OrElse.secondary(IO.ioEffect), contextShift)
        fb <- get(df).start
        _  <- IO(assertEquals(Thread.currentThread().getName, name))
        _  <- df.release
        _  <- IO(assertEquals(Thread.currentThread().getName, name))
        _  <- fb.join
      } yield ()

      val dt = 10.seconds
      assert(task.unsafeRunTimed(dt).nonEmpty, s"; timed-out after $dt")
    }
  }

  test("Semaphore (async) — issue #380: with foreverM and latch") { implicit ec =>
    for (_ <- 0 until iterations) {
      val cancelLoop = new AtomicBoolean(false)
      val unit = IO {
        if (cancelLoop.get()) throw new CancellationException
      }

      try {
        val task = for {
          df    <- Semaphore[IO](0)(OrElse.primary(IO.ioConcurrentEffect), contextShift)
          latch <- Deferred.uncancelable[IO, Unit]
          fb    <- (latch.complete(()) *> df.acquire *> unit.foreverM).start
          _     <- latch.get
          _     <- df.release.timeout(timeout).guarantee(fb.cancel)
        } yield ()

        assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      } finally {
        cancelLoop.set(true)
      }
    }
  }

  test("Semaphore (async) — issue #380: with foreverM and no latch") { implicit ec =>
    for (_ <- 0 until iterations) {
      val cancelLoop = new AtomicBoolean(false)
      val unit = IO {
        if (cancelLoop.get()) throw new CancellationException
      }

      try {
        val task = for {
          df <- Semaphore[IO](0)(OrElse.secondary(IO.ioEffect), contextShift)
          fb <- (df.acquire *> unit.foreverM).start
          _  <- df.release.timeout(timeout).guarantee(fb.cancel)
        } yield ()

        assert(task.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
      } finally {
        cancelLoop.set(true)
      }
    }
  }

  test("Semaphore (async) — issue #380: with cooperative light async boundaries and latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.async[Unit](cb => cb(Right(()))) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d     <- Semaphore[IO](0)(OrElse.secondary(IO.ioEffect), contextShift)
        latch <- Deferred.uncancelable[IO, Unit]
        fb    <- (latch.complete(()) *> d.acquire *> foreverAsync(0)).start
        _     <- latch.get
        _     <- d.release.timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  test("Semaphore (async) — issue #380: with cooperative light async boundaries and no latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.async[Unit](cb => cb(Right(()))) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d  <- Semaphore[IO](0)(OrElse.secondary(IO.ioEffect), contextShift)
        fb <- (d.acquire *> foreverAsync(0)).start
        _  <- d.release.timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  test("Semaphore (async) — issue #380: with cooperative full async boundaries and latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.unit.start.flatMap(_.join) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d     <- Semaphore[IO](0)(OrElse.secondary(IO.ioEffect), contextShift)
        latch <- Deferred.uncancelable[IO, Unit]
        fb    <- (latch.complete(()) *> d.acquire *> foreverAsync(0)).start
        _     <- latch.get
        _     <- d.release.timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }

  test("Semaphore (async) — issue #380: with cooperative full async boundaries and no latch") { implicit ec =>
    def run = {
      def foreverAsync(i: Int): IO[Unit] = {
        if (i == 512) IO.unit.start.flatMap(_.join) >> foreverAsync(0)
        else IO.unit >> foreverAsync(i + 1)
      }

      for {
        d  <- Semaphore[IO](0)(OrElse.secondary(IO.ioEffect), contextShift)
        fb <- (d.acquire *> foreverAsync(0)).start
        _  <- d.release.timeout(timeout).guarantee(fb.cancel)
      } yield true
    }

    for (_ <- 0 until iterations) {
      assert(run.unsafeRunTimed(timeout).nonEmpty, s"; timed-out after $timeout")
    }
  }
}
