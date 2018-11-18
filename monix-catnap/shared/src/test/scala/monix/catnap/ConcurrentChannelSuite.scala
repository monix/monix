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

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import minitest.SimpleTestSuite
import monix.execution.BufferCapacity.{Bounded, Unbounded}
import monix.execution.ChannelType.{MPMC, MPSC, SPMC, SPSC}
import monix.execution.internal.Platform
import monix.execution.{BufferCapacity, Scheduler}
import scala.concurrent.Future
import scala.concurrent.duration._

object ConcurrentChannelSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = Scheduler.global

  val iterationsCount = {
    if (Platform.isJVM) {
      // Discriminate CI
      if (System.getenv("TRAVIS") == "true" || System.getenv("CI") == "true")
        2000
      else
        10000
    } else {
      100 // JavaScript
    }
  }

  implicit def contextShift(implicit s: Scheduler): ContextShift[IO] =
    s.contextShift[IO](IO.ioEffect)
  implicit def timer(implicit s: Scheduler): Timer[IO] =
    s.timerLiftIO[IO](IO.ioEffect)

  def testIO(name: String)(f: => IO[Unit]) =
    testAsync(name) {
      f.timeout(10.seconds).unsafeToFuture()
    }

  testIO("simple push and pull") {
    for {
      chan <- ConcurrentChannel[IO].custom[Int, Int](Bounded(10))
      consume = chan.consume.use { consumer =>
        for {
          r1 <- consumer.pull
          r2 <- consumer.pull
          r3 <- consumer.pull
          r4 <- consumer.pull
        } yield {
          assertEquals(r1, Right(1))
          assertEquals(r2, Right(2))
          assertEquals(r3, Right(3))
          assertEquals(r4, Left(0))
        }
      }
      _     <- consume.start
      _     <- chan.push(1)
      _     <- chan.push(2)
      _     <- chan.push(3)
      _     <- chan.halt(0)
    } yield ()
  }

  testAsync("pullMany back-pressuring for minLength, with maxLength") {
    def test(times: Int): Future[Unit] = {
      val channel = ConcurrentChannel[IO].unsafe[Int, Int]()
      val batch = channel.consume.use(_.pullMany(10, 10))
        .map(_.right.map(_.sum))
        .unsafeToFuture()

      assertEquals(batch.value, None)

      def loop(n: Int): IO[Unit] =
        channel.push(n).flatMap { _ =>
          if (n - 1 > 0) loop(n - 1)
          else IO.unit
        }

      val f = for {
        _ <- channel.awaitConsumers(1).unsafeToFuture()
        _ <- loop(9).unsafeToFuture()
        _ <- loop(10).unsafeToFuture()
        r <- batch
      } yield {
        assertEquals(r, Right(5 * 11))
      }

      if (times > 1)
        f.flatMap(_ => test(times - 1))
      else
        f
    }
    test(times = if (Platform.isJVM) 1000 else 1)
  }

  testIO("concurrent sum via consumer.pull; MPMC; producers=4, consumers=4, workers=4, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      workersPerConsumer = 4,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; MPMC; producers=4, consumers=4, workers=4, capacity=Unbounded") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      workersPerConsumer = 4,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; SPMC; producers=1, consumers=4, workers=4, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 1,
      consumers = 4,
      workersPerConsumer = 4,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; SPMC; producers=1, consumers=4, workers=4, capacity=Unbounded") {
    testConcurrentSum(
      producers = 1,
      consumers = 4,
      workersPerConsumer = 4,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; MPMC; producers=4, consumers=1, workers=4, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 4,
      consumers = 1,
      workersPerConsumer = 4,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; MPMC; producers=4, consumers=1, workers=4, capacity=Unbounded") {
    testConcurrentSum(
      producers = 4,
      consumers = 1,
      workersPerConsumer = 4,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; MPSC; producers=4, consumers=4, workers=1, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      workersPerConsumer = 1,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; MPSC; producers=4, consumers=4, workers=1, capacity=Unbounded") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      workersPerConsumer = 1,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; SPSC; producers=1, consumers=1, workers=1, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 1,
      consumers = 1,
      workersPerConsumer = 1,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pull; SPSC; producers=1, consumers=1, workers=1, capacity=Unbounded") {
    testConcurrentSum(
      producers = 1,
      consumers = 1,
      workersPerConsumer = 1,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = false
    )
  }

  testIO("concurrent sum via consumer.pullMany; MPMC; producers=4, consumers=4, workers=4, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      workersPerConsumer = 4,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; MPMC; producers=4, consumers=4, workers=4, capacity=Unbounded") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      workersPerConsumer = 4,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; SPMC; producers=1, consumers=4, workers=4, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 1,
      consumers = 4,
      workersPerConsumer = 4,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; SPMC; producers=1, consumers=4, workers=4, capacity=Unbounded") {
    testConcurrentSum(
      producers = 1,
      consumers = 4,
      workersPerConsumer = 4,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; MPMC; producers=4, consumers=1, workers=4, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 4,
      consumers = 1,
      workersPerConsumer = 4,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; MPMC; producers=4, consumers=1, workers=4, capacity=Unbounded") {
    testConcurrentSum(
      producers = 4,
      consumers = 1,
      workersPerConsumer = 4,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; MPSC; producers=4, consumers=4, workers=1, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      workersPerConsumer = 1,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; MPSC; producers=4, consumers=4, workers=1, capacity=Unbounded") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      workersPerConsumer = 1,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; SPSC; producers=1, consumers=1, workers=1, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 1,
      consumers = 1,
      workersPerConsumer = 1,
      capacity = Bounded(16),
      count = iterationsCount,
      pullMany = true
    )
  }

  testIO("concurrent sum via consumer.pullMany; SPSC; producers=1, consumers=1, workers=1, capacity=Unbounded") {
    testConcurrentSum(
      producers = 1,
      consumers = 1,
      workersPerConsumer = 1,
      capacity = Unbounded(),
      count = iterationsCount,
      pullMany = true
    )
  }

  def testConcurrentSum(
    producers: Int,
    consumers: Int,
    workersPerConsumer: Int,
    capacity: BufferCapacity,
    count: Int,
    pullMany: Boolean): IO[Unit] = {

    val channelType =
      if (producers > 1) {
        if (workersPerConsumer > 1) MPMC
        else MPSC
      } else {
        if (workersPerConsumer > 1) SPMC
        else SPSC
      }

    def consume(consumer: ConsumerF[IO, Int, Int]): IO[Long] = {
      def worker(acc: Long): IO[Long] = {
        if (pullMany)
          consumer.pullMany(1, 16).flatMap {
            case Left(i) => IO.pure(acc + i)
            case Right(seq) =>
              assert(seq.length <= 16)
              worker(acc + seq.sum)
          }
        else
          consumer.pull.flatMap {
            case Left(i) => IO.pure(acc + i)
            case Right(i) => worker(acc + i)
          }
      }

      if (workersPerConsumer > 1) {
        val list = (0 until workersPerConsumer).map(_ => worker(0)).toList
        list.parSequence.map(_.sum)
      } else {
        worker(0)
      }
    }

    def consumeMany(channel: ConcurrentChannel[IO, Int, Int]): IO[Long] = {
      val task = channel
        .consumeCustom(capacity, channelType.consumerType)
        .use(ref => consume(ref))

      if (consumers < 2) {
        task
      } else {
        val list = (0 until consumers).map(_ => task).toList
        list.parSequence.map(_.sum)
      }
    }

    def produce(channel: ConcurrentChannel[IO, Int, Int]): IO[Unit] = {
      def loop(channel: ConcurrentChannel[IO, Int, Int], n: Int): IO[Unit] =
        if (n > 0) channel.push(n).flatMap(_ => loop(channel, n - 1))
        else IO.unit

      val task = loop(channel, count)
      if (producers < 2)
        task
      else
        (0 until producers).map(_ => task).toList.parSequence_
    }

    for {
      channel <- ConcurrentChannel[IO].custom[Int, Int](producerType = channelType.producerType)
      fiber   <- consumeMany(channel).start
      _       <- channel.awaitConsumers(consumers)
      _       <- produce(channel)
      _       <- channel.halt(0)
      sum     <- fiber.join
    } yield {
      val perProducer = count.toLong * (count + 1) / 2
      assertEquals(sum, perProducer * producers * consumers)
    }
  }
}
