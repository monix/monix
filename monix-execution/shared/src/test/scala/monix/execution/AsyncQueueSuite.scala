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

package monix.execution

import java.util.concurrent.atomic.AtomicLong
import minitest.TestSuite
import monix.execution.ChannelType.{MPMC, MPSC, SPMC, SPSC}
import monix.execution.schedulers.TestScheduler
import monix.execution.internal.Platform
import scala.concurrent.Future
import scala.util.Success

object AsyncQueueSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  test("simple offer and poll") { implicit s =>
    val queue = AsyncQueue[Int](capacity = 10)

    queue.offer(1)
    queue.offer(2)
    queue.offer(3)

    assertEquals(queue.poll().value, Some(Success(1)))
    assertEquals(queue.poll().value, Some(Success(2)))
    assertEquals(queue.poll().value, Some(Success(3)))
  }

  test("async poll") { implicit s =>
    val queue = AsyncQueue[Int](capacity = 10)

    queue.offer(1)
    assertEquals(queue.poll().value, Some(Success(1)))

    val f = queue.poll()
    assertEquals(f.value, None)

    queue.offer(2); s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("drain (TestScheduler)") { implicit s =>
    val queue = AsyncQueue[Int](capacity = 10)

    val count = 1000
    val elems = for (i <- 0 until count) yield i
    queue.offerMany(elems:_*)

    val f = queue.drain(1000, 1000); s.tick()
    assertEquals(f.value.flatMap(_.map(_.sum).toOption), Some(count * (count - 1) / 2))
  }

  testAsync("drain (global)") { _ =>
    import Scheduler.Implicits.global
    val queue = AsyncQueue[Int](capacity = 10)

    val count = 1000
    val elems = for (i <- 0 until count) yield i
    val f1 = queue.offerMany(elems:_*)
    val f2 = queue.drain(1000, 1000)

    for (_ <- f1; r2 <- f2) yield {
      assertEquals(r2.sum, count * (count - 1) / 2)
    }
  }

  test("clear") { implicit s =>
    val queue = AsyncQueue[Int](capacity = 10)

    queue.offer(1)
    queue.clear()

    val value = queue.tryPoll()
    assertEquals(value, None)
  }

  test("concurrent producer - consumer; MPMC; TestScheduler") { implicit s =>
    val f = testConcurrency(1000, 3, MPMC)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; MPSC; TestScheduler") { implicit s =>
    val f = testConcurrency(1000, 1, MPSC)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPMC; TestScheduler") { implicit s =>
    val f = testConcurrency(1000, 3, SPMC)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPSC; TestScheduler") { implicit s =>
    val f = testConcurrency(1000, 1, SPSC)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  testAsync("concurrent producer - consumer; MPMC; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    testConcurrency(count, 3, MPMC)
  }

  testAsync("concurrent producer - consumer; MPSC; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    testConcurrency(count, 1, MPSC)
  }

  testAsync("concurrent producer - consumer; SPMC; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    testConcurrency(count, 3, SPMC)
  }

  testAsync("concurrent producer - consumer; SPSC; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    testConcurrency(count, 1, SPSC)
  }

  def testConcurrency(n: Int, workers: Int, ct: ChannelType)
    (implicit s: Scheduler): Future[Unit] = {

    val queue = AsyncQueue[Int](capacity = 128, channelType = ct)

    def producer(n: Int): CancelableFuture[Unit] =
      if (n > 0)
        queue.offer(n).flatMap(_ => producer(n - 1))
      else
        queue.offerMany((for (_ <- 0 until workers) yield 0) :_*)

    val atomic = new AtomicLong(0)
    def consumer(): Future[Unit] =
      queue.poll().flatMap { i =>
        if (i > 0) {
          atomic.addAndGet(i)
          consumer()
        } else {
          CancelableFuture.unit
        }
      }

    val futures = producer(n) +: (0 until workers).map(_ => consumer())
    for (_ <- Future.sequence(futures)) yield {
      assertEquals(atomic.get(), n.toLong * (n + 1) / 2)
    }
  }
}
