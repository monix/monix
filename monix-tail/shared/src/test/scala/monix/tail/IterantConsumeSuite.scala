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

package monix.tail

import cats.laws._
import cats.laws.discipline._
import minitest.SimpleTestSuite
import monix.catnap.ConcurrentChannel
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.internal.Platform
import monix.tail.Iterant.Consumer

import scala.collection.mutable.ListBuffer

object IterantConsumeSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = Scheduler.Implicits.global

  testAsync("iterant.pushToChannel") {
    val count = if (Platform.isJVM) 10000 else 100

    def pushLoop(channel: ConcurrentChannel[Task, Option[Throwable], Int], n: Int): Task[Unit] =
      if (n > 0) channel.push(1).flatMap(_ => pushLoop(channel, n - 1))
      else channel.halt(None)

    val task = for {
      channel <- ConcurrentChannel[Task].of[Option[Throwable], Int]
      fiber   <- channel.consume.use(c => foldConsumerViaPullOne(c, 0L)(_ + _)).start
      _       <- channel.awaitConsumers(1)
      _       <- pushLoop(channel, count)
      sum     <- fiber.join
    } yield {
      assertEquals(sum, count)
    }
    task.runToFuture
  }

  testAsync("iterant.consume (pull)") {
    val count = if (Platform.isJVM) 10000 else 100

    val source = Iterant[Task].range(0, count)
    val task = for (sum <- foldIterantPullOne(source, 0L)(_ + _)) yield {
      assertEquals(sum, count.toLong * (count - 1) / 2)
    }
    task.runToFuture
  }

  testAsync("iterant.consume (pullMany)") {
    val count = if (Platform.isJVM) 10000 else 100

    val source = Iterant[Task].range(0, count)
    val task = for (sum <- foldIterantPullMany(source, 0L)(_ + _)) yield {
      assertEquals(sum, count.toLong * (count - 1) / 2)
    }
    task.runToFuture
  }

  def foldIterantPullOne[S, A](iter: Iterant[Task, A], seed: => S)(f: (S, A) => S): Task[S] =
    Task.suspend {
      iter.consume.use(foldConsumerViaPullOne(_, seed)(f))
    }

  def foldConsumerViaPullOne[S, A](channel: Consumer[Task, A], acc: S)(f: (S, A) => S): Task[S] =
    channel.pull.flatMap {
      case Right(a) => foldConsumerViaPullOne(channel, f(acc, a))(f)
      case Left(None) => Task.now(acc)
      case Left(Some(e)) => Task.raiseError(e)
    }

  def foldIterantPullMany[S, A](iter: Iterant[Task, A], seed: => S)(f: (S, A) => S): Task[S] =
    Task.suspend {
      iter.consume.use(foldConsumerViaPullMany(_, seed)(f))
    }

  def foldConsumerViaPullMany[S, A](channel: Consumer[Task, A], acc: S)(f: (S, A) => S): Task[S] =
    channel.pullMany(1, 256).flatMap {
      case Right(seq) => foldConsumerViaPullOne(channel, seq.foldLeft(acc)(f))(f)
      case Left(None) => Task.now(acc)
      case Left(Some(e)) => Task.raiseError(e)
    }
}
