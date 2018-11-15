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
import minitest.SimpleTestSuite
import monix.execution.BufferCapacity.Bounded
import monix.execution.Scheduler

object ConcurrentChannelSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = Scheduler.global

  implicit def contextShift(implicit s: Scheduler): ContextShift[IO] =
    s.contextShift[IO](IO.ioEffect)
  implicit def timer(implicit s: Scheduler): Timer[IO] =
    s.timerLiftIO[IO](IO.ioEffect)

  def testIO(name: String)(f: => IO[Unit]) =
    testAsync(name)(f.unsafeToFuture())

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
}
