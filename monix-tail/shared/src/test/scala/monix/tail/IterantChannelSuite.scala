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

package monix.tail

import cats.implicits._
import cats.effect.{ ContextShift, IO, Timer }
import minitest.SimpleTestSuite
import monix.catnap.ProducerF
import monix.execution.BufferCapacity.{ Bounded, Unbounded }
import monix.execution.ChannelType.{ MultiProducer, SingleProducer }
import monix.execution.internal.Platform
import monix.execution.{ BufferCapacity, Scheduler }
import monix.catnap.SchedulerEffect

object IterantChannelSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = Scheduler.global

  implicit def contextShift(implicit s: Scheduler): ContextShift[IO] =
    SchedulerEffect.contextShift[IO](s)(IO.ioEffect)
  implicit def timer(implicit s: Scheduler): Timer[IO] =
    SchedulerEffect.timerLiftIO[IO](s)(IO.ioEffect)

  def testIO(name: String)(f: => IO[Unit]) =
    testAsync(name)(f.unsafeToFuture())

  testIO("concurrent sum; producers=4, consumers=4, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      capacity = Bounded(16),
      count = if (Platform.isJVM) 100000 else 100
    )
  }

  testIO("concurrent sum; producers=4, consumers=4, capacity=Unbounded") {
    testConcurrentSum(
      producers = 4,
      consumers = 4,
      capacity = Unbounded(),
      count = if (Platform.isJVM) 100000 else 100
    )
  }

  testIO("concurrent sum; producers=1, consumers=4, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 1,
      consumers = 4,
      capacity = Bounded(16),
      count = if (Platform.isJVM) 100000 else 100
    )
  }

  testIO("concurrent sum; producers=1, consumers=4, capacity=Unbounded") {
    testConcurrentSum(
      producers = 1,
      consumers = 4,
      capacity = Unbounded(),
      count = if (Platform.isJVM) 100000 else 100
    )
  }

  testIO("concurrent sum; producers=4, consumers=1, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 4,
      consumers = 1,
      capacity = Bounded(16),
      count = if (Platform.isJVM) 100000 else 100
    )
  }

  testIO("concurrent sum; producers=4, consumers=1, capacity=Unbounded") {
    testConcurrentSum(
      producers = 4,
      consumers = 1,
      capacity = Unbounded(),
      count = if (Platform.isJVM) 100000 else 100
    )
  }

  testIO("concurrent sum; producers=1, consumers=1, capacity=Bounded(16)") {
    testConcurrentSum(
      producers = 1,
      consumers = 1,
      capacity = Bounded(16),
      count = if (Platform.isJVM) 100000 else 100
    )
  }

  testIO("concurrent sum; producers=1, consumers=1, capacity=Unbounded") {
    testConcurrentSum(
      producers = 1,
      consumers = 1,
      capacity = Unbounded(),
      count = if (Platform.isJVM) 100000 else 100
    )
  }

  def testConcurrentSum(producers: Int, consumers: Int, capacity: BufferCapacity, count: Int) = {

    def produce(channel: ProducerF[IO, Option[Throwable], Int]): IO[Unit] = {
      def loop(channel: ProducerF[IO, Option[Throwable], Int], n: Int): IO[Unit] =
        if (n > 0) channel.push(n).flatMap {
          case true => loop(channel, n - 1)
          case false => IO.unit
        }
        else {
          IO.unit
        }

      val task = loop(channel, count)
      if (producers < 2)
        task
      else
        (0 until producers).map(_ => task).toList.parSequence_
    }

    def consumeMany(channel: Iterant[IO, Int]): IO[Long] = {
      val task = channel.foldLeftL(0L)(_ + _)
      if (consumers < 2) {
        task
      } else {
        val list = (0 until consumers).map(_ => task).toList
        list.parSequence.map(_.sum)
      }
    }

    val pt = if (producers > 1) MultiProducer else SingleProducer

    Iterant[IO].channel[Int](capacity, producerType = pt).flatMap {
      case (producer, stream) =>
        for {
          fiber <- consumeMany(stream).start
          _     <- producer.awaitConsumers(consumers)
          _     <- produce(producer)
          _     <- producer.halt(None)
          sum   <- fiber.join
        } yield {
          val perProducer = count.toLong * (count + 1) / 2
          assertEquals(sum, perProducer * producers * consumers)
        }
    }
  }
}
