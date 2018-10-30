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

import java.util.concurrent.atomic.AtomicLong

import cats.implicits._
import cats.effect.{ContextShift, IO, Timer}
import minitest.TestSuite
import monix.execution.ChannelType.{MPMC, MPSC, SPMC, SPSC}
import monix.execution.{ChannelType, Scheduler}
import monix.execution.schedulers.TestScheduler
import monix.execution.internal.Platform
import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.util.Success

object ConcurrentQueueSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  implicit def contextShift(implicit s: Scheduler): ContextShift[IO] =
    s.contextShift[IO](IO.ioEffect)
  implicit def timer(implicit s: Scheduler): Timer[IO] =
    s.timerLiftIO[IO](IO.ioEffect)

  test("simple offer and poll") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](capacity = 10)

    queue.offer(1).unsafeRunSync()
    queue.offer(2).unsafeRunSync()
    queue.offer(3).unsafeRunSync()

    assertEquals(queue.poll.unsafeRunSync(), 1)
    assertEquals(queue.poll.unsafeRunSync(), 2)
    assertEquals(queue.poll.unsafeRunSync(), 3)
  }

  test("async poll") { implicit s =>
    val queue = ConcurrentQueue[IO].of[Int](capacity = 10).unsafeRunSync()

    queue.offer(1).unsafeRunSync()
    assertEquals(queue.poll.unsafeRunSync(), 1)

    val f = queue.poll.unsafeToFuture()
    assertEquals(f.value, None)

    queue.offer(2).unsafeRunSync(); s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("offer/poll over capacity (TestScheduler)") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](capacity = 10)
    val count = 1000

    def producer(n: Int): IO[Unit] =
      if (n > 0) queue.offer(count - n).flatMap(_ => producer(n - 1))
      else IO.unit

    def consumer(n: Int, acc: Queue[Int] = Queue.empty): IO[Long] =
      if (n > 0)
        queue.poll.flatMap { a => consumer(n - 1, acc.enqueue(a)) }
      else
        IO.pure(acc.sum)

    val p = producer(count).unsafeToFuture()
    val f = consumer(count).unsafeToFuture()

    s.tick()
    assertEquals(p.value, Some(Success(())))
    assertEquals(f.value.flatMap(_.toOption), Some(count * (count - 1) / 2))
  }

  testAsync("offer/poll over capacity (global)") { _ =>
    import Scheduler.Implicits.global
    val queue = ConcurrentQueue[IO].unsafe[Int](capacity = 10)
    val count = 1000

    def producer(n: Int): IO[Unit] =
      if (n > 0) queue.offer(count - n).flatMap(_ => producer(n - 1))
      else IO.unit

    def consumer(n: Int, acc: Queue[Int] = Queue.empty): IO[Long] =
      if (n > 0)
        queue.poll.flatMap { a => consumer(n - 1, acc.enqueue(a)) }
      else
        IO.pure(acc.sum)

    val p = producer(count).unsafeToFuture()
    val f = consumer(count).unsafeToFuture()

    for (_ <- p; r <- f) yield {
      assertEquals(r, count * (count - 1) / 2)
    }
  }

  test("drain (TestScheduler)") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](capacity = 10)

    val count = 1000
    val elems = for (i <- 0 until count) yield i

    val p = queue.offerMany(elems:_*).unsafeToFuture()
    val f = queue.drain(count, count).unsafeToFuture()

    s.tick()
    assertEquals(p.value, Some(Success(())))
    assertEquals(f.value.flatMap(_.map(_.sum).toOption), Some(count * (count - 1) / 2))
  }

  testAsync("drain (global)") { _ =>
    import Scheduler.Implicits.global
    val queue = ConcurrentQueue[IO].unsafe[Int](capacity = 10)

    val count = 1000
    val elems = for (i <- 0 until count) yield i
    val f1 = queue.offerMany(elems:_*).unsafeToFuture()
    val f2 = queue.drain(1000, 1000).unsafeToFuture()

    for (_ <- f1; r2 <- f2) yield {
      assertEquals(r2.sum, count * (count - 1) / 2)
    }
  }

  test("clear") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](capacity = 10)

    queue.offer(1).unsafeRunSync()
    queue.clear.unsafeRunSync()

    val value = queue.tryPoll.unsafeRunSync()
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

    val queue = ConcurrentQueue[IO].unsafe[Int](capacity = 128, channelType = ct)

    def producer(n: Int): IO[Unit] =
      if (n > 0)
        queue.offer(n).flatMap(_ => producer(n - 1))
      else
        queue.offerMany((for (_ <- 0 until workers) yield 0) :_*)

    val atomic = new AtomicLong(0)
    def consumer(): IO[Unit] =
      queue.poll.flatMap { i =>
        if (i > 0) {
          atomic.addAndGet(i)
          consumer()
        } else {
          IO.unit
        }
      }

    val tasks = (producer(n) +: (0 until workers).map(_ => consumer())).toList
    for (_ <- tasks.parSequence.unsafeToFuture()) yield {
      assertEquals(atomic.get(), n.toLong * (n + 1) / 2)
    }
  }
}
