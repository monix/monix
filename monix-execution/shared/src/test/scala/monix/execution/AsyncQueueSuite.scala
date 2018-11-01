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
import monix.execution.BufferCapacity.{Bounded, Unbounded}
import monix.execution.ChannelType.{MPMC, MPSC, SPMC, SPSC}
import monix.execution.schedulers.TestScheduler
import monix.execution.internal.Platform

import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.util.Success

object AsyncQueueSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  def testSimpleOfferAndPoll(queue: AsyncQueue[Int])(implicit s: Scheduler) = {
    queue.offer(1)
    queue.offer(2)
    queue.offer(3)

    assertEquals(queue.poll().value, Some(Success(1)))
    assertEquals(queue.poll().value, Some(Success(2)))
    assertEquals(queue.poll().value, Some(Success(3)))
  }

  test("simple offer and poll (bounded)") { implicit s =>
    testSimpleOfferAndPoll(AsyncQueue.bounded[Int](capacity = 10))
  }

  test("simple offer and poll (unbounded)") { implicit s =>
    testSimpleOfferAndPoll(AsyncQueue.unbounded[Int]())
  }

  def testAsyncPoll(queue: AsyncQueue[Int])(implicit s: TestScheduler) = {
    queue.offer(1)
    assertEquals(queue.poll().value, Some(Success(1)))

    val f = queue.poll()
    assertEquals(f.value, None)

    queue.offer(2); s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("async poll (bounded)") { implicit s =>
    testAsyncPoll(AsyncQueue.bounded[Int](capacity = 10))
  }

  test("async poll (unbounded)") { implicit s =>
    testAsyncPoll(AsyncQueue.bounded[Int](capacity = 10))
  }

  def tryOfferTryPollTest(queue: AsyncQueue[Int])(implicit s: Scheduler) = {
    val count = 1000

    def producer(n: Int): Future[Unit] =
      if (n > 0) Future(queue.tryOffer(count - n)).flatMap {
        case true => producer(n - 1)
        case false => producer(n)
      } else {
        Future.successful(())
      }

    def consumer(n: Int, acc: Queue[Int] = Queue.empty): Future[Long] =
      if (n > 0)
        Future(queue.tryPoll()).flatMap {
          case Some(a) => consumer(n - 1, acc.enqueue(a))
          case None => consumer(n, acc)
        }
      else
        Future.successful(acc.sum)

    val p = producer(count)
    val c = consumer(count)

    for (_ <- p; r <- c) yield {
      assertEquals(r, count * (count - 1) / 2)
    }
  }

  test("tryOffer / tryPoll / bounded / TestScheduler") { implicit s =>
    tryOfferTryPollTest(AsyncQueue.bounded[Int](capacity = 16))(s)
    s.tick()
  }

  test("tryOffer / tryPoll / unbounded / TestScheduler") { implicit s =>
    tryOfferTryPollTest(AsyncQueue.unbounded[Int]())(s)
    s.tick()
  }

  testAsync("tryOffer / tryPoll / bounded / global") { _ =>
    import Scheduler.Implicits.global
    tryOfferTryPollTest(AsyncQueue.bounded[Int](capacity = 16))(global)
  }

  testAsync("tryOffer / tryPoll / unbounded / global") { _ =>
    import Scheduler.Implicits.global
    tryOfferTryPollTest(AsyncQueue.unbounded[Int]())(global)
  }

  test("drain (TestScheduler)") { implicit s =>
    val queue = AsyncQueue.bounded[Int](capacity = 10)

    val count = 1000
    val elems = for (i <- 0 until count) yield i
    queue.offerMany(elems:_*)

    val f = queue.drain(1000, 1000); s.tick()
    assertEquals(f.value.flatMap(_.map(_.sum).toOption), Some(count * (count - 1) / 2))
  }

  testAsync("drain (global)") { _ =>
    import Scheduler.Implicits.global
    val queue = AsyncQueue.bounded[Int](capacity = 10)

    val count = 1000
    val elems = for (i <- 0 until count) yield i
    val f1 = queue.offerMany(elems:_*)
    val f2 = queue.drain(1000, 1000)

    for (_ <- f1; r2 <- f2) yield {
      assertEquals(r2.sum, count * (count - 1) / 2)
    }
  }

  test("clear") { implicit s =>
    val queue = AsyncQueue.bounded[Int](capacity = 10)

    queue.offer(1)
    queue.clear()

    val value = queue.tryPoll()
    assertEquals(value, None)
  }

  test("concurrent producer - consumer; MPMC; bounded  ; TestScheduler") { implicit s =>
    val queue = AsyncQueue.custom[Int](Bounded(128), MPMC)
    val f = testConcurrency(queue, 1000, 3)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; MPMC; unbounded; TestScheduler") { implicit s =>
    val queue = AsyncQueue.custom[Int](Unbounded(), MPMC)
    val f = testConcurrency(queue, 1000, 3)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; MPSC; bounded  ; TestScheduler") { implicit s =>
    val queue = AsyncQueue.custom[Int](Bounded(128), MPSC)
    val f = testConcurrency(queue, 1000, 1)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; MPSC; unbounded; TestScheduler") { implicit s =>
    val queue = AsyncQueue.custom[Int](Unbounded(), MPSC)
    val f = testConcurrency(queue, 1000, 1)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPMC; bounded  ; TestScheduler") { implicit s =>
    val queue = AsyncQueue.custom[Int](Bounded(128), SPMC)
    val f = testConcurrency(queue, 1000, 3)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPMC; unbounded; TestScheduler") { implicit s =>
    val queue = AsyncQueue.custom[Int](Unbounded(), SPMC)
    val f = testConcurrency(queue, 1000, 3)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPSC; bounded  ; TestScheduler") { implicit s =>
    val queue = AsyncQueue.custom[Int](Bounded(128), SPSC)
    val f = testConcurrency(queue, 1000, 1)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("concurrent producer - consumer; SPSC; unbounded; TestScheduler") { implicit s =>
    val queue = AsyncQueue.custom[Int](Bounded(128), SPSC)
    val f = testConcurrency(queue, 1000, 1)
    s.tick()
    assertEquals(f.value, Some(Success(())))
  }

  testAsync("concurrent producer - consumer; MPMC; bounded  ; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.custom[Int](Bounded(128), MPMC)
    testConcurrency(queue, count, 3)
  }

  testAsync("concurrent producer - consumer; MPMC; unbounded; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.custom[Int](Unbounded(), MPMC)
    testConcurrency(queue, count, 3)
  }

  testAsync("concurrent producer - consumer; MPSC; bounded  ; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.custom[Int](Bounded(128), MPSC)
    testConcurrency(queue, count, 1)
  }

  testAsync("concurrent producer - consumer; MPSC; unbounded; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.custom[Int](Unbounded(), MPSC)
    testConcurrency(queue, count, 1)
  }

  testAsync("concurrent producer - consumer; SPMC; bounded  ; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.custom[Int](Bounded(128), SPMC)
    testConcurrency(queue, count, 3)
  }

  testAsync("concurrent producer - consumer; SPMC; unbounded; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.custom[Int](Unbounded(), SPMC)
    testConcurrency(queue, count, 3)
  }

  testAsync("concurrent producer - consumer; SPSC; bounded  ; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.custom[Int](Bounded(128), SPSC)
    testConcurrency(queue, count, 1)
  }

  testAsync("concurrent producer - consumer; SPSC; unbounded; real scheduler") { _ =>
    import Scheduler.Implicits.global
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.custom[Int](Unbounded(), SPSC)
    testConcurrency(queue, count, 1)
  }

  def testConcurrency(queue: AsyncQueue[Int], n: Int, workers: Int)
    (implicit s: Scheduler): Future[Unit] = {

    def producer(n: Int): Future[Unit] = {
      def offerViaTry(a: Int): Future[Unit] =
        Future.successful(queue.tryOffer(a)).flatMap {
          case true => Future.successful(())
          case false => offerViaTry(a)
        }

      if (n > 0) {
        val offer = if (n % 2 == 0) queue.offer(n) else offerViaTry(n)
        offer.flatMap(_ => producer(n - 1))
      } else {
        queue.offerMany((for (_ <- 0 until workers) yield 0) :_*)
      }
    }

    val atomic = new AtomicLong(0)
    def consumer(idx: Int = 0): Future[Unit] = {
      def pollViaTry(): Future[Int] =
        Future.successful(queue.tryPoll()).flatMap {
          case Some(v) => Future.successful(v)
          case None => pollViaTry()
        }

      val poll =
        if (idx % 2 == 0) queue.poll()
        else pollViaTry()

      poll.flatMap { i =>
        if (i > 0) {
          atomic.addAndGet(i)
          consumer(idx + 1)
        } else {
          CancelableFuture.unit
        }
      }
    }

    val futures = producer(n) +: (0 until workers).map(_ => consumer())
    for (_ <- Future.sequence(futures)) yield {
      assertEquals(atomic.get(), n.toLong * (n + 1) / 2)
    }
  }
}
