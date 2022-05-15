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

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import monix.execution.internal.Platform
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Random, Success}

object AsyncSemaphoreSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  test("simple greenLight") { implicit s =>
    val semaphore = AsyncSemaphore(provisioned = 4)
    val future = semaphore.withPermit(() => Future(100))
    assertEquals(semaphore.available(), 3)
    assert(!future.isCompleted, "!future.isCompleted")

    s.tick()
    assertEquals(future.value, Some(Success(100)))
    assertEquals(semaphore.available(), 4)
  }

  test("should back-pressure when full") { implicit s =>
    val semaphore = AsyncSemaphore(provisioned = 2)

    val p1 = Promise[Int]()
    val f1 = semaphore.withPermit(() => p1.future)
    val p2 = Promise[Int]()
    val f2 = semaphore.withPermit(() => p2.future)

    s.tick()
    assertEquals(semaphore.available(), 0)

    val f3 = semaphore.withPermit(() => Future(3))

    s.tick()
    assertEquals(f3.value, None)
    assertEquals(semaphore.available(), 0)

    p1.success(1); s.tick()
    assertEquals(semaphore.available(), 1)
    assertEquals(f1.value, Some(Success(1)))
    assertEquals(f3.value, Some(Success(3)))

    p2.success(2); s.tick()
    assertEquals(f2.value, Some(Success(2)))
    assertEquals(semaphore.available(), 2)
  }

  testAsync("real async test of many futures") { _ =>
    // Executing Futures on the global scheduler!
    import scala.concurrent.ExecutionContext.Implicits.global

    val semaphore = AsyncSemaphore(provisioned = 4)
    val count = if (Platform.isJVM) 100000 else 1000

    val futures = for (i <- 0 until count) yield semaphore.withPermit(() => Future(i)): Future[Int]
    val sum =
      Future.sequence(futures).map(_.sum)

    // Asynchronous result, to be handled by Minitest
    for (result <- sum) yield {
      assertEquals(result, count * (count - 1) / 2)
    }
  }

  test("await for release of all active and pending permits") { implicit s =>
    val semaphore = AsyncSemaphore(provisioned = 2)
    val p1 = semaphore.acquire()
    assertEquals(p1.value, Some(Success(())))
    val p2 = semaphore.acquire()
    assertEquals(p2.value, Some(Success(())))

    val p3 = semaphore.acquire()
    assert(!p3.isCompleted, "!p3.isCompleted")
    val p4 = semaphore.acquire()
    assert(!p4.isCompleted, "!p4.isCompleted")

    val all1 = semaphore.awaitAvailable(2)
    assert(!all1.isCompleted, "!all1.isCompleted")

    semaphore.release(); s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release(); s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release(); s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release(); s.tick()
    assert(all1.isCompleted, "all1.isCompleted")

    // REDO
    val p5 = semaphore.acquire()
    assert(p5.isCompleted, "p5.isCompleted")
    val all2 = semaphore.awaitAvailable(2)
    s.tick(); assert(!all2.isCompleted, "!all2.isCompleted")
    semaphore.release(); s.tick()
    assert(all2.isCompleted, "all2.isCompleted")

    // Already completed
    val all3 = semaphore.awaitAvailable(2)
    assert(all3.isCompleted, "all3.isCompleted")
  }

  test("acquire is cancelable") { implicit s =>
    val semaphore = AsyncSemaphore(provisioned = 2)

    val p1 = semaphore.acquire()
    assert(p1.isCompleted, "p1.isCompleted")
    val p2 = semaphore.acquire()
    assert(p2.isCompleted, "p2.isCompleted")

    val p3 = semaphore.acquire()
    assert(!p3.isCompleted, "!p3.isCompleted")
    assertEquals(semaphore.available(), 0)

    p3.cancel()
    semaphore.release()
    assertEquals(semaphore.available(), 1)
    semaphore.release()
    assertEquals(semaphore.available(), 2)

    s.tick()
    assertEquals(semaphore.available(), 2)
    assert(!p3.isCompleted, "!p3.isCompleted")
  }

  testAsync("withPermitN / awaitAvailable concurrent test") { _ =>
    // Executing Futures on the global scheduler!
    import scala.concurrent.ExecutionContext.Implicits.global

    repeatTest(100) { () =>
      val available = 6L
      val semaphore = AsyncSemaphore(provisioned = available)
      val count = if (Platform.isJVM) 10000 else 100
      val allReleased = Promise[Unit]()

      val f = semaphore.withPermit { () =>
        allReleased.completeWith(semaphore.awaitAvailable(available))

        val futures = for (i <- 0 until count) yield {
          val n = (Math.floorMod(Random.nextInt(), 3) + 1).toLong
          semaphore.withPermitN(n) { () =>
            Future(1).map { x =>
              assert(!allReleased.isCompleted, s"!allReleased.isCompleted (index $i)")
              x
            }
          }: Future[Int]
        }
        Future.sequence(futures).map { x =>
          x.sum
        }
      }

      for (r <- f; _ <- allReleased.future) yield {
        assertEquals(r, count)
        assertEquals(semaphore.available(), available)
      }
    }
  }

  testAsync("tryAcquireN / awaitAvailable concurrent test") { _ =>
    // Executing Futures on the global scheduler!
    import scala.concurrent.ExecutionContext.Implicits.global

    def acquireN(semaphore: AsyncSemaphore, n: Long): Future[Unit] =
      Future(semaphore.tryAcquireN(n)).flatMap {
        case true => Future.successful(())
        case false => acquireN(semaphore, n)
      }

    def withPermitN[A](semaphore: AsyncSemaphore, n: Long)(f: () => Future[A]): Future[A] =
      acquireN(semaphore, n).flatMap { _ =>
        FutureUtils.transform[A, A](f(), r => { semaphore.releaseN(n); r })
      }

    repeatTest(10) { () =>
      val available = 6L
      val semaphore = AsyncSemaphore(provisioned = available)
      val count = if (Platform.isJVM) 1000 else 100
      val allReleased = Promise[Unit]()

      val f = withPermitN(semaphore, 1) { () =>
        allReleased.completeWith(semaphore.awaitAvailable(available))

        val futures = for (i <- 0 until count) yield {
          withPermitN(semaphore, (Math.floorMod(Random.nextInt(), 3) + 1).toLong) { () =>
            Future(1).map { x =>
              assert(!allReleased.isCompleted, s"!allReleased.isCompleted (index $i)")
              x
            }
          }
        }
        Future.sequence(futures).map { x =>
          x.sum
        }
      }

      for (r <- f; _ <- allReleased.future) yield {
        assertEquals(r, count)
        assertEquals(semaphore.available(), available)
      }
    }
  }

  def repeatTest(n: Int)(f: () => Future[Unit])(implicit ec: ExecutionContext): Future[Unit] =
    if (n > 0) f().flatMap(_ => repeatTest(n - 1)(f))
    else Future.successful(())
}
