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

import cats.effect.IO
import cats.implicits._
import minitest.TestSuite
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Random, Success}

import TestUtils._
import cats.effect.kernel.Deferred

object SemaphoreSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  test("simple permit") { implicit s =>
    val semaphore = Semaphore.unsafe[IO](provisioned = 4)
    val p = Deferred.unsafe[IO, Unit]
    val future = semaphore.permit.surround(p.get *> IO(100)).unsafeToFuture()
    s.tick()

    assertEquals(semaphore.available.unsafeRunSyncTick(), 3)
    assert(!future.isCompleted, "!future.isCompleted")
    p.complete(()).unsafeToFuture()

    s.tick()
    assertEquals(future.value, Some(Success(100)))
    assertEquals(semaphore.available.unsafeRunSyncTick(), 4)
  }

  test("should back-pressure when full") { implicit s =>
    val semaphore = Semaphore.unsafe[IO](provisioned = 2)

    val p1 = Promise[Int]()
    val f1 = semaphore.permit.surround(IO.fromFuture(IO.pure(p1.future))).unsafeToFuture()
    val p2 = Promise[Int]()
    val f2 = semaphore.permit.surround(IO.fromFuture(IO.pure(p2.future))).unsafeToFuture()

    s.tick()
    assertEquals(semaphore.available.unsafeRunSyncTick(), 0)

    val f3 = semaphore.permit.surround(IO(3)).unsafeToFuture()

    s.tick()
    assertEquals(f3.value, None)
    assertEquals(semaphore.available.unsafeRunSyncTick(), 0)

    p1.success(1); s.tick()
    assertEquals(semaphore.available.unsafeRunSyncTick(), 1)
    assertEquals(f1.value, Some(Success(1)))
    assertEquals(f3.value, Some(Success(3)))

    p2.success(2); s.tick()
    assertEquals(f2.value, Some(Success(2)))
    assertEquals(semaphore.available.unsafeRunSyncTick(), 2)
  }

  testAsync("real async test of many futures") { _ =>
    // Executing Futures on the global scheduler!
    import monix.execution.Scheduler.Implicits.global

    val semaphore = Semaphore.unsafe[IO](provisioned = 20)
    val count = if (Platform.isJVM) 10000 else 1000

    val futures = for (i <- 0 until count) yield semaphore.permit.surround(IO.cede *> IO(i))
    val sum =
      futures.toList.parSequence.map(_.sum).unsafeToFuture()

    // Asynchronous result, to be handled by Minitest
    for (result <- sum) yield {
      assertEquals(result, count * (count - 1) / 2)
    }
  }

  test("await for release of all active and pending permits") { implicit s =>
    val semaphore = Semaphore.unsafe[IO](provisioned = 2)
    val p1 = semaphore.acquire.unsafeToFuture()
    s.tick()
    assertEquals(p1.value, Some(Success(())))
    val p2 = semaphore.acquire.unsafeToFuture()
    s.tick()
    assertEquals(p2.value, Some(Success(())))

    val p3 = semaphore.acquire.unsafeToFuture()
    s.tick()
    assert(!p3.isCompleted, "!p3.isCompleted")
    val p4 = semaphore.acquire.unsafeToFuture()
    s.tick()
    assert(!p4.isCompleted, "!p4.isCompleted")

    val all1 = semaphore.awaitAvailable(2).unsafeToFuture()
    s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")

    semaphore.release.unsafeToFuture(); s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release.unsafeToFuture(); s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release.unsafeToFuture(); s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release.unsafeToFuture(); s.tick()
    assert(all1.isCompleted, "all1.isCompleted")

    // REDO
    val p5 = semaphore.acquire.unsafeToFuture()
    s.tick()
    assert(p5.isCompleted, "p5.isCompleted")
    val all2 = semaphore.awaitAvailable(2).unsafeToFuture()
    s.tick(); assert(!all2.isCompleted, "!all2.isCompleted")
    semaphore.release.unsafeToFuture(); s.tick()
    assert(all2.isCompleted, "all2.isCompleted")

    // Already completed
    val all3 = semaphore.awaitAvailable(2).unsafeToFuture(); s.tick()
    assert(all3.isCompleted, "all3.isCompleted")
  }

  test("acquire is cancelable") { implicit s =>
    val semaphore = Semaphore.unsafe[IO](provisioned = 2)

    val p1 = semaphore.acquire.unsafeToFuture()
    s.tick()
    assert(p1.isCompleted, "p1.isCompleted")
    val p2 = semaphore.acquire.unsafeToFuture()
    s.tick()
    assert(p2.isCompleted, "p2.isCompleted")

    val p3 = Promise[Unit]()
    val cancel = semaphore.acquire.unsafeRunCancelableTick() { _ => p3.success(()); () }
    assert(!p3.isCompleted, "!p3.isCompleted")
    assertEquals(semaphore.available.unsafeRunSyncTick(), 0)

    cancel.unsafeRunSyncTick()
    semaphore.release.unsafeToFuture(); s.tick()
    assertEquals(semaphore.available.unsafeRunSyncTick(), 1)
    semaphore.release.unsafeRunSyncTick(); s.tick()
    assertEquals(semaphore.available.unsafeRunSyncTick(), 2)

    s.tick()
    assertEquals(semaphore.available.unsafeRunSyncTick(), 2)
    assert(!p3.isCompleted, "!p3.isCompleted")
  }

  testAsync("permits(n) / awaitAvailable concurrent test") { _ =>
    // Executing Futures on the global scheduler!
    import monix.execution.Scheduler.Implicits.global

    val task = repeatTest(10) {
      val available = 6L
      val semaphore = Semaphore.unsafe[IO](provisioned = available)
      val count = if (Platform.isJVM) 10000 else 50
      val allReleased = Promise[Unit]()

      val task = semaphore.permit.surround(IO.defer {
        allReleased.completeWith(semaphore.awaitAvailable(available).unsafeToFuture())

        val futures = for (i <- 0 until count) yield {
          semaphore.permits(Math.floorMod(Random.nextInt(), 3).toLong + 1).surround {
            IO(1).map { x =>
              assert(!allReleased.isCompleted, s"!allReleased.isCompleted (index $i)")
              x
            }
          }
        }
        futures.toList.parSequence.map { x =>
          x.sum
        }
      })

      for {
        r <- task
        _ <- IO.fromFuture(IO.pure(allReleased.future))
        availableNow <- semaphore.available
      } yield {
        assertEquals(r, count)
        assertEquals(availableNow, available)
      }
    }
    task.unsafeToFuture()
  }

  test("permits(n) has FIFO priority") { implicit s =>
    val sem = Semaphore.unsafe[IO](provisioned = 0)

    val f1 = sem.permits(3).surround(IO(1 + 1)).unsafeToFuture(); s.tick()
    assertEquals(f1.value, None)
    val f2 = sem.permits(4).surround(IO(1 + 1)).unsafeToFuture(); s.tick()
    assertEquals(f2.value, None)

    sem.releaseN(2).unsafeRunAndForget(); s.tick()
    assertEquals(f1.value, None)
    assertEquals(f2.value, None)

    sem.releaseN(1).unsafeRunAndForget(); s.tick()
    assertEquals(f1.value, Some(Success(2)))
    assertEquals(f2.value, None)

    sem.releaseN(1).unsafeRunAndForget(); s.tick()
    assertEquals(f2.value, Some(Success(2)))
  }

  test("permits(n) is cancelable (1)") { implicit s =>
    val sem = Semaphore.unsafe[IO](provisioned = 0)
    assertEquals(sem.count.unsafeRunSyncTick(), 0)

    val p1 = Promise[Int]()
    val cancel = sem.permits(3).surround(IO(1 + 1)).unsafeRunCancelableTick() { r => p1.complete(r.toTry); () }
    val f2 = sem.permits(3).surround(IO(1 + 1)).unsafeToFuture()
    s.tick()

    assertEquals(p1.future.value, None)
    assertEquals(f2.value, None)
    assertEquals(sem.count.unsafeRunSyncTick(), -6)

    cancel.unsafeRunAndForget(); s.tick()
    assertEquals(sem.count.unsafeRunSyncTick(), -3)

    sem.releaseN(3).unsafeRunAndForget()
    s.tick()

    assertEquals(p1.future.value, None)
    assertEquals(f2.value, Some(Success(2)))
  }

  test("permits(n) is cancelable (2)") { implicit s =>
    val sem = Semaphore.unsafe[IO](provisioned = 1)

    val p1 = Promise[Int]()
    val cancel = sem.permits(3).surround(IO(1 + 1)).unsafeRunCancelableTick() { r => p1.complete(r.toTry); () }
    val f2 = sem.permits(3).surround(IO(1 + 1)).unsafeToFuture()
    s.tick()
    assertEquals(sem.count.unsafeRunSyncTick(), -5)

    sem.releaseN(1).unsafeRunAndForget()
    s.tick()
    assertEquals(sem.count.unsafeRunSyncTick(), -4)

    assertEquals(p1.future.value, None)
    assertEquals(f2.value, None)

    cancel.unsafeRunAndForget(); s.tick()
    assertEquals(sem.count.unsafeRunSyncTick(), -1)

    sem.releaseN(1).unsafeRunAndForget()
    s.tick()

    assertEquals(p1.future.value, None)
    assertEquals(f2.value, Some(Success(2)))
  }

  def repeatTest(n: Int)(f: => IO[Unit]): IO[Unit] =
    if (n > 0) f.flatMap(_ => repeatTest(n - 1)(f))
    else IO.unit
}
