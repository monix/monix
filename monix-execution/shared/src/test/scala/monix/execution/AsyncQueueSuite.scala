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

import java.util.concurrent.atomic.AtomicLong
import minitest.TestSuite
import monix.execution.BufferCapacity.{Bounded, Unbounded}
import monix.execution.ChannelType.{MPMC, MPSC, SPMC, SPSC}
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.concurrent.duration._

object AsyncQueueFakeSuite extends BaseAsyncQueueSuite[TestScheduler] {
  def setup() = TestScheduler()

  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  def testFuture(name: String, times: Int)(f: Scheduler => Future[Unit]): Unit = {
    def repeatTest(test: Future[Unit], n: Int)(implicit ec: Scheduler): Future[Unit] =
      if (n > 0)
        test.flatMap(_ => repeatTest(test, n - 1))
      else
        Future.successful(())

    test(name) { implicit ec =>
      repeatTest(f(ec), times)
      ec.tick(1.day)
    }
  }
}

object AsyncQueueGlobalSuite extends BaseAsyncQueueSuite[Scheduler] {
  def setup() = Scheduler.global
  def tearDown(env: Scheduler): Unit = ()

  def testFuture(name: String, times: Int)(f: Scheduler => Future[Unit]): Unit = {
    def repeatTest(test: Future[Unit], n: Int)(implicit ec: Scheduler): Future[Unit] =
      if (n > 0)
        FutureUtils
          .timeout(test, 60.seconds)
          .flatMap(_ => repeatTest(test, n - 1))
      else
        Future.successful(())

    testAsync(name) { implicit ec =>
      repeatTest(f(ec), times)
    }
  }
}

abstract class BaseAsyncQueueSuite[S <: Scheduler] extends TestSuite[S] {
  val repeatForFastTests = {
    if (Platform.isJVM) 1000 else 100
  }
  val repeatForSlowTests = {
    if (Platform.isJVM) 50 else 1
  }

  /** TO IMPLEMENT ... */
  def testFuture(name: String, times: Int = 1)(f: Scheduler => Future[Unit]): Unit

  testFuture("simple offer and poll", times = repeatForFastTests) { implicit s =>
    val queue = AsyncQueue.bounded[Int](10)
    for {
      _ <- queue.offer(1)
      _ <- queue.offer(2)
      _ <- queue.offer(3)
      r1 <- queue.poll()
      r2 <- queue.poll()
      r3 <- queue.poll()
    } yield {
      assertEquals(r1, 1)
      assertEquals(r2, 2)
      assertEquals(r3, 3)
    }
  }

  testFuture("async poll", times = repeatForFastTests) { implicit s =>
    val queue = AsyncQueue.bounded[Int](10)
    for {
      _ <- queue.offer(1)
      r1 <- queue.poll()
      _ <- Future(assertEquals(r1, 1))
      f <- Future(queue.poll())
      _ <- Future(assertEquals(f.value, None))
      _ <- queue.offer(2)
      r2 <- f
    } yield {
      assertEquals(r2, 2)
    }
  }

  testFuture("offer/poll over capacity", times = repeatForFastTests) { implicit s =>
    val queue = AsyncQueue.bounded[Long](10)
    val count = 1000L

    def producer(n: Long): Future[Unit] =
      if (n > 0) queue.offer(count - n).flatMap(_ => producer(n - 1))
      else Future.successful(())

    def consumer(n: Long, acc: Queue[Long] = Queue.empty): Future[Long] =
      if (n > 0)
        queue.poll().flatMap { a =>
          consumer(n - 1, acc.enqueue(a))
        }
      else
        Future.successful(acc.foldLeft(0L)(_ + _))

    val p = producer(count)
    val c = consumer(count)
    for {
      _ <- p
      r <- c
    } yield {
      assertEquals(r, count * (count - 1) / 2)
    }
  }

  testFuture("tryOffer / tryPoll", times = repeatForFastTests) { implicit ec =>
    val queue = AsyncQueue.bounded[Long](16)
    val count = 1000L

    def producer(n: Long): Future[Unit] =
      if (n > 0) Future(queue.tryOffer(count - n)).flatMap {
        case true =>
          producer(n - 1)
        case false =>
          FutureUtils.delayedResult(10.millis)(()).flatMap(_ => producer(n))
      }
      else {
        Future.successful(())
      }

    def consumer(n: Long, acc: Queue[Long] = Queue.empty): Future[Long] =
      if (n > 0)
        Future(queue.tryPoll()).flatMap {
          case Some(a) => consumer(n - 1, acc.enqueue(a))
          case None =>
            FutureUtils.delayedResult(10.millis)(()).flatMap(_ => consumer(n, acc))
        }
      else
        Future.successful(acc.foldLeft(0L)(_ + _))

    val c = consumer(count)
    val p = producer(count)
    for {
      _ <- p
      r <- c
    } yield {
      assertEquals(r, count * (count - 1) / 2)
    }
  }

  testFuture("drain; MPMC; unbounded", times = repeatForFastTests) { implicit ec =>
    testDrain(Unbounded(), MPMC)
  }

  testFuture("drain; MPSC; unbounded", times = repeatForFastTests) { implicit ec =>
    testDrain(Unbounded(), MPSC)
  }

  testFuture("drain; SPMC; unbounded", times = repeatForFastTests) { implicit ec =>
    testDrain(Unbounded(), SPMC)
  }

  testFuture("drain; SPMC; unbounded", times = repeatForFastTests) { implicit ec =>
    testDrain(Unbounded(), SPSC)
  }

  testFuture("drain; MPMC; bounded", times = repeatForFastTests) { implicit ec =>
    testDrain(Bounded(32), MPMC)
  }

  testFuture("drain; MPSC; bounded", times = repeatForFastTests) { implicit ec =>
    testDrain(Bounded(32), MPSC)
  }

  testFuture("drain; SPMC; bounded", times = repeatForFastTests) { implicit ec =>
    testDrain(Bounded(32), SPMC)
  }

  testFuture("drain; SPMC; bounded", times = repeatForFastTests) { implicit ec =>
    testDrain(Bounded(32), SPSC)
  }

  def testDrain(bc: BufferCapacity, ct: ChannelType)(implicit ec: Scheduler): Future[Unit] = {
    val count = 1000
    val elems = for (i <- 0 until count) yield i
    val queue = AsyncQueue.withConfig[Int](bc, ct)

    val f1 = queue.drain(1000, 1000)
    val f2 = queue.offerMany(elems)
    for {
      _ <- f2
      r <- f1
    } yield {
      assertEquals(r.sum, count * (count - 1) / 2)
    }
  }

  testFuture("clear") { implicit s =>
    val queue = AsyncQueue.bounded[Int](10)
    for {
      _ <- queue.offer(1)
      _ <- Future(queue.clear())
      r <- Future(queue.tryPoll())
    } yield {
      assertEquals(r, None)
    }
  }

  testFuture("clear after overflow") { implicit s =>
    val queue = AsyncQueue.bounded[Int](512)
    val fiber = queue.offerMany(0 until 1000)
    for {
      _ <- FutureUtils.timeoutTo(fiber, 3.millis, Future.successful(()))
      _ <- Future(queue.clear())
      _ <- fiber
    } yield ()
  }

  testFuture("concurrent producer - consumer; MPMC; bounded") { implicit ec =>
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.withConfig[Int](Bounded(128), MPMC)
    testConcurrency(queue, count, 3)
  }

  testFuture("concurrent producer - consumer; MPMC; unbounded") { implicit ec =>
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.withConfig[Int](Unbounded(), MPMC)
    testConcurrency(queue, count, 3)
  }

  testFuture("concurrent producer - consumer; MPSC; bounded") { implicit ec =>
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.withConfig[Int](Bounded(128), MPSC)
    testConcurrency(queue, count, 1)
  }

  testFuture("concurrent producer - consumer; MPSC; unbounded") { implicit ec =>
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.withConfig[Int](Unbounded(), MPSC)
    testConcurrency(queue, count, 1)
  }

  testFuture("concurrent producer - consumer; SPMC; bounded") { implicit ec =>
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.withConfig[Int](Bounded(128), SPMC)
    testConcurrency(queue, count, 3)
  }

  testFuture("concurrent producer - consumer; SPMC; unbounded") { implicit ec =>
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.withConfig[Int](Unbounded(), SPMC)
    testConcurrency(queue, count, 3)
  }

  testFuture("concurrent producer - consumer; SPSC; bounded") { implicit ec =>
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.withConfig[Int](Bounded(128), SPSC)
    testConcurrency(queue, count, 1)
  }

  testFuture("concurrent producer - consumer; SPSC; unbounded") { implicit ec =>
    val count = if (Platform.isJVM) 10000 else 1000
    val queue = AsyncQueue.withConfig[Int](Unbounded(), SPSC)
    testConcurrency(queue, count, 1)
  }

  def testConcurrency(queue: AsyncQueue[Int], n: Int, workers: Int)(implicit s: Scheduler): Future[Unit] = {

    def producer(n: Int): Future[Unit] = {
      def offerViaTry(n: Int): Future[Unit] =
        Future(queue.tryOffer(n)).flatMap {
          case true => Future.successful(())
          case false =>
            FutureUtils.delayedResult(10.millis)(()).flatMap(_ => offerViaTry(n))
        }

      if (n > 0) {
        val offer = if (n % 2 == 0) queue.offer(n) else offerViaTry(n)
        offer.flatMap(_ => producer(n - 1))
      } else {
        queue.offerMany(for (_ <- 0 until workers) yield 0)
      }
    }

    val atomic = new AtomicLong(0)
    def consumer(idx: Int = 0): Future[Unit] = {
      def pollViaTry(): Future[Int] =
        Future(queue.tryPoll()).flatMap {
          case Some(v) => Future.successful(v)
          case None =>
            FutureUtils.delayedResult(10.millis)(()).flatMap(_ => pollViaTry())
        }

      val poll = if (idx % 2 == 0) queue.poll() else pollViaTry()
      poll.flatMap { i =>
        if (i > 0) {
          atomic.addAndGet(i.toLong)
          consumer(idx + 1)
        } else {
          Future.successful(())
        }
      }
    }

    val tasks = (producer(n) +: (0 until workers).map(_ => consumer())).toList
    for (_ <- Future.sequence(tasks)) yield {
      assertEquals(atomic.get(), n.toLong * (n + 1) / 2)
    }
  }
}
