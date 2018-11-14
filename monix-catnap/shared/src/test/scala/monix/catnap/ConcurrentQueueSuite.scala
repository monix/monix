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
import monix.execution.BufferCapacity.{Bounded, Unbounded}
import monix.execution.ChannelType.{MPMC, MPSC, SPMC, SPSC}
import monix.execution.Scheduler
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
    val queue = ConcurrentQueue[IO].custom[Int](Bounded(10)).unsafeRunSync()

    queue.offer(1).unsafeRunSync()
    queue.offer(2).unsafeRunSync()
    queue.offer(3).unsafeRunSync()

    assertEquals(queue.poll.unsafeRunSync(), 1)
    assertEquals(queue.poll.unsafeRunSync(), 2)
    assertEquals(queue.poll.unsafeRunSync(), 3)
  }

  test("async poll") { implicit s =>
    val queue = ConcurrentQueue[IO].bounded[Int](10).unsafeRunSync()

    queue.offer(1).unsafeRunSync()
    assertEquals(queue.poll.unsafeRunSync(), 1)

    val f = queue.poll.unsafeToFuture()
    assertEquals(f.value, None)

    queue.offer(2).unsafeRunSync(); s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("offer/poll over capacity (TestScheduler)") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(10))
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
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(10))
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

  def tryOfferTryPollTest(implicit s: Scheduler) = {
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(16))
    val count = 1000

    def producer(n: Int): IO[Unit] =
      if (n > 0) queue.tryOffer(count - n).flatMap {
        case true => producer(n - 1)
        case false => IO.shift *> producer(n)
      } else {
        IO.unit
      }

    def consumer(n: Int, acc: Queue[Int] = Queue.empty): IO[Long] =
      if (n > 0)
        queue.tryPoll.flatMap {
          case Some(a) => consumer(n - 1, acc.enqueue(a))
          case None => IO.shift *> consumer(n, acc)
        }
      else
        IO.pure(acc.sum)

    (producer(count), consumer(count)).parMapN {
      case (_, r) => assertEquals(r, count * (count - 1) / 2)
    }.unsafeToFuture()
  }

  test("tryOffer / tryPoll (TestScheduler)") { implicit s =>
    tryOfferTryPollTest(s)
    s.tick()
  }

  testAsync("tryOffer / tryPoll (global)") { _ =>
    import Scheduler.Implicits.global
    tryOfferTryPollTest(global)
  }

  test("drain (TestScheduler)") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(10))

    val count = 1000
    val elems = for (i <- 0 until count) yield i

    val p = queue.offerMany(elems).unsafeToFuture()
    val f = queue.drain(count, count).unsafeToFuture()

    s.tick()
    assertEquals(p.value, Some(Success(())))
    assertEquals(f.value.flatMap(_.map(_.sum).toOption), Some(count * (count - 1) / 2))
  }

  testAsync("drain (global)") { _ =>
    import Scheduler.Implicits.global
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(10))

    val count = 1000
    val elems = for (i <- 0 until count) yield i
    val f1 = queue.offerMany(elems).unsafeToFuture()
    val f2 = queue.drain(1000, 1000).unsafeToFuture()

    for (_ <- f1; r2 <- f2) yield {
      assertEquals(r2.sum, count * (count - 1) / 2)
    }
  }

  test("clear") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(10))

    queue.offer(1).unsafeRunSync()
    queue.clear.unsafeRunSync()

    val value = queue.tryPoll.unsafeRunSync()
    assertEquals(value, None)
  }


  test("concurrent producer - consumer; MPMC; bounded  ; TestScheduler") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), MPMC)
    val f = testConcurrency(queue, 1000, 3)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; MPMC; unbounded; TestScheduler") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Unbounded(), MPMC)
    val f = testConcurrency(queue, 1000, 3)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; MPSC; bounded  ; TestScheduler") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), MPSC)
    val f = testConcurrency(queue, 1000, 1)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; MPSC; unbounded; TestScheduler") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Unbounded(), MPSC)
    val f = testConcurrency(queue, 1000, 1)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPMC; bounded  ; TestScheduler") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), SPMC)
    val f = testConcurrency(queue, 1000, 3)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPMC; unbounded; TestScheduler") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Unbounded(), SPMC)
    val f = testConcurrency(queue, 1000, 3)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPSC; bounded  ; TestScheduler") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), SPSC)
    val f = testConcurrency(queue, 1000, 1)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPSC; unbounded; TestScheduler") { implicit s =>
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), SPSC)
    val f = testConcurrency(queue, 1000, 1)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  testAsync("concurrent producer - consumer; MPMC; bounded  ; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), MPMC)
    testConcurrency(queue, count, 3)
  }

  testAsync("concurrent producer - consumer; MPMC; unbounded; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = ConcurrentQueue[IO].unsafe[Int](Unbounded(), MPMC)
    testConcurrency(queue, count, 3)
  }

  testAsync("concurrent producer - consumer; MPSC; bounded  ; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), MPSC)
    testConcurrency(queue, count, 1)
  }

  testAsync("concurrent producer - consumer; MPSC; unbounded; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = ConcurrentQueue[IO].unsafe[Int](Unbounded(), MPSC)
    testConcurrency(queue, count, 1)
  }

  testAsync("concurrent producer - consumer; SPMC; bounded  ; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), SPMC)
    testConcurrency(queue, count, 3)
  }

  testAsync("concurrent producer - consumer; SPMC; unbounded; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = ConcurrentQueue[IO].unsafe[Int](Unbounded(), SPMC)
    testConcurrency(queue, count, 3)
  }

  testAsync("concurrent producer - consumer; SPSC; bounded  ; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = ConcurrentQueue[IO].unsafe[Int](Bounded(128), SPSC)
    testConcurrency(queue, count, 1)
  }

  testAsync("concurrent producer - consumer; SPSC; unbounded; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = ConcurrentQueue[IO].unsafe[Int](Unbounded(), SPSC)
    testConcurrency(queue, count, 1)
  }

  def testConcurrency(queue: ConcurrentQueue[IO, Int], n: Int, workers: Int)
    (implicit s: Scheduler): Future[Unit] = {

    def producer(n: Int): IO[Unit] = {
      def offerViaTry(n: Int): IO[Unit] =
        queue.tryOffer(n).flatMap {
          case true => IO.unit
          case false => offerViaTry(n)
        }

      if (n > 0) {
        val offer = if (n % 2 == 0) queue.offer(n) else offerViaTry(n)
        offer.flatMap(_ => producer(n - 1))
      } else {
        queue.offerMany(for (_ <- 0 until workers) yield 0)
      }
    }

    val atomic = new AtomicLong(0)
    def consumer(idx: Int = 0): IO[Unit] = {
      def pollViaTry: IO[Int] =
        queue.tryPoll.flatMap {
          case Some(v) => IO.pure(v)
          case None => IO.shift *> pollViaTry
        }

      val poll = if (idx % 2 == 0) queue.poll else pollViaTry
      poll.flatMap { i =>
        if (i > 0) {
          atomic.addAndGet(i)
          consumer(idx + 1)
        } else {
          IO.unit
        }
      }
    }

    val tasks = (producer(n) +: (0 until workers).map(_ => consumer())).toList
    for (_ <- tasks.parSequence.unsafeToFuture()) yield {
      assertEquals(atomic.get(), n.toLong * (n + 1) / 2)
    }
  }
}
