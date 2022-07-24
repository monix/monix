/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import java.util.concurrent.Executors
import monix.execution.BaseTestSuite
import cats.effect.IO
import cats.implicits._
import monix.execution.atomic.Atomic
import scala.concurrent.{ CancellationException, ExecutionContext }
import scala.concurrent.duration._

class CatsEffectIssue380Suite extends BaseTestSuite {
  test("MVar does not block on put — typelevel/cats-effect#380") {
    val service = Executors.newSingleThreadScheduledExecutor()
    implicit val ec = ExecutionContext.global
    implicit val cs = IO.contextShift(ec)
    implicit val timer = IO.timer(ec, service)

    try {
      for (_ <- 0 until 10) {
        val cancelLoop = Atomic(false)
        val unit = IO {
          if (cancelLoop.get()) throw new CancellationException
        }

        try {
          val task = for {
            mv <- MVar[IO].empty[Unit]()
            _  <- (mv.take *> unit.foreverM).start
            _  <- timer.sleep(100.millis)
            _  <- mv.put(())
          } yield ()

          val dt = 10.seconds
          assert(task.unsafeRunTimed(dt).nonEmpty, s"timed-out after $dt")
        } finally {
          cancelLoop.set(true)
        }
      }
    } finally {
      service.shutdown()
    }
  }

  test("Semaphore does not block on release — typelevel/cats-effect#380") {
    val service = Executors.newSingleThreadScheduledExecutor()
    implicit val ec = ExecutionContext.global
    implicit val cs = IO.contextShift(ec)
    implicit val timer = IO.timer(ec, service)

    try {
      for (_ <- 0 until 10) {
        val cancelLoop = Atomic(false)
        val unit = IO {
          if (cancelLoop.get()) throw new CancellationException
        }

        try {
          val task = for {
            mv <- Semaphore[IO](0)
            _  <- (mv.acquire *> unit.foreverM).start
            _  <- timer.sleep(100.millis)
            _  <- mv.release
          } yield ()

          val dt = 10.seconds
          assert(task.unsafeRunTimed(dt).nonEmpty, s"timed-out after $dt")
        } finally {
          cancelLoop.set(true)
        }
      }
    } finally {
      service.shutdown()
    }
  }
}
