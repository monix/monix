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

import monix.catnap.ConcurrentChannel
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.internal.Platform
import monix.tail.Iterant.Consumer
import scala.concurrent.Future
import scala.util.Random

object IterantConsumeSuite extends BaseTestSuite {
  val iterationsCount = {
    if (Platform.isJVM) {
      // Discriminate CI
      if (isCI)
        2000
      else
        10000
    } else {
      100 // JavaScript
    }
  }

  testAsync("iterant.pushToChannel (simple)") { _ =>
    implicit val ec: Scheduler = Scheduler.Implicits.global
    val stream = Iterant[Task].range(0, iterationsCount)

    val task = for {
      channel <- ConcurrentChannel[Task].of[Option[Throwable], Int]
      fiber <- channel.consume.use(c => foldConsumerViaPullOne(c, 0L)(_ + _)).start
      _ <- channel.awaitConsumers(1)
      _ <- stream.pushToChannel(channel)
      sum <- fiber.join
    } yield {
      assertEquals(sum, iterationsCount.toLong * (iterationsCount - 1) / 2)
    }
    task.runToFuture
  }

  testAsync("iterant.pushToChannel (arbitrary)") { _ =>
    implicit val ec: Scheduler = Scheduler.Implicits.global

    def loop(times: Int): Future[Unit] = {
      val list = Range(0, iterationsCount).toList
      val stream = arbitraryListToIterant[Task, Int](list, Random.nextInt(), allowErrors = false)

      val task = for {
        channel <- ConcurrentChannel[Task].of[Option[Throwable], Int]
        fiber <- channel.consume.use(c => foldConsumerViaPullOne(c, 0L)(_ + _)).start
        _ <- channel.awaitConsumers(1)
        _ <- stream.pushToChannel(channel)
        sum <- fiber.join
      } yield {
        assertEquals(sum, iterationsCount.toLong * (iterationsCount - 1) / 2)
      }

      val f = task.runToFuture
      if (times > 0)
        f.flatMap(_ => loop(times - 1))
      else
        f
    }
    loop(100)
  }

  testAsync("iterant.consume (pull)") { _ =>
    implicit val ec: Scheduler = Scheduler.Implicits.global
    val list = Range(0, iterationsCount).toList
    val source = arbitraryListToIterant[Task, Int](list, Random.nextInt(), allowErrors = false)

    val task = for (sum <- foldIterantPullOne(source, 0L)(_ + _)) yield {
      assertEquals(sum, iterationsCount.toLong * (iterationsCount - 1) / 2)
    }
    task.runToFuture
  }

  testAsync("iterant.consume (pullMany)") { _ =>
    implicit val ec: Scheduler = Scheduler.Implicits.global
    val list = Range(0, iterationsCount).toList
    val source = arbitraryListToIterant[Task, Int](list, Random.nextInt(), allowErrors = false)

    val task = for (sum <- foldIterantPullMany(source, 0L)(_ + _)) yield {
      assertEquals(sum, iterationsCount.toLong * (iterationsCount - 1) / 2)
    }
    task.runToFuture
  }

  testAsync("Iterant.channel") { _ =>
    implicit val ec: Scheduler = Scheduler.Implicits.global

    val task = Iterant[Task].channel[Int]().flatMap {
      case (producer, stream) =>
        val write = Iterant[Task].range(0, iterationsCount).pushToChannel(producer)
        for {
          _ <- (producer.awaitConsumers(1) *> write).start
          sum <- stream.foldLeftL(0L)(_ + _)
        } yield {
          assertEquals(sum, iterationsCount.toLong * (iterationsCount - 1) / 2)
        }
    }
    task.runToFuture
  }

  def foldIterantPullOne[S, A](iter: Iterant[Task, A], seed: => S)(f: (S, A) => S): Task[S] =
    Task.suspend {
      iter.toChannel.consume.use(foldConsumerViaPullOne(_, seed)(f))
    }

  def foldConsumerViaPullOne[S, A](channel: Consumer[Task, A], acc: S)(f: (S, A) => S): Task[S] =
    channel.pull.flatMap {
      case Right(a) => foldConsumerViaPullOne(channel, f(acc, a))(f)
      case Left(None) => Task.now(acc)
      case Left(Some(e)) => Task.raiseError(e)
    }

  def foldIterantPullMany[S, A](iter: Iterant[Task, A], seed: => S)(f: (S, A) => S): Task[S] =
    Task.suspend {
      iter.toChannel.consume.use(foldConsumerViaPullMany(_, seed)(f))
    }

  def foldConsumerViaPullMany[S, A](channel: Consumer[Task, A], acc: S)(f: (S, A) => S): Task[S] =
    channel.pullMany(1, 256).flatMap {
      case Right(seq) => foldConsumerViaPullOne(channel, seq.foldLeft(acc)(f))(f)
      case Left(None) => Task.now(acc)
      case Left(Some(e)) => Task.raiseError(e)
    }
}
