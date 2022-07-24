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

package monix.tail

import cats.effect.{ IO, Timer }
import monix.eval.Task

import scala.util.Success
import scala.concurrent.duration._
import monix.catnap.SchedulerEffect

class IntervalIntervalSuite extends BaseTestSuite {
  fixture.test("Iterant[Task].intervalWithFixedDelay(1.second, 2.seconds)") { implicit s =>
    var effect = 0
    val lst = Iterant[Task]
      .intervalWithFixedDelay(1.second, 2.seconds)
      .map { e =>
        effect += 1; e
      }
      .take(5)
      .toListL
      .runToFuture

    assertEquals(lst.value, None)
    assertEquals(effect, 0)

    s.tick(1.second)
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 1)
    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(2.seconds)
    assertEquals(effect, 3)
    s.tick(2.seconds)
    assertEquals(effect, 4)
    s.tick(2.seconds)
    assertEquals(effect, 5)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L, 3L, 4L))))
  }

  fixture.test("Iterant[IO].intervalWithFixedDelay(1.second, 2.seconds)") { s =>
    implicit val timer: Timer[IO] = SchedulerEffect.timerLiftIO[IO](s)(IO.ioEffect)

    var effect = 0
    val lst = Iterant[IO]
      .intervalWithFixedDelay(1.second, 2.seconds)
      .map { e =>
        effect += 1; e
      }
      .take(5)
      .toListL
      .unsafeToFuture()

    assertEquals(lst.value, None)
    assertEquals(effect, 0)

    s.tick(1.second)
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 1)
    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(2.seconds)
    assertEquals(effect, 3)
    s.tick(2.seconds)
    assertEquals(effect, 4)
    s.tick(2.seconds)
    assertEquals(effect, 5)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L, 3L, 4L))))
  }

  fixture.test("Iterant[Task].intervalWithFixedDelay(2.seconds)") { implicit s =>
    var effect = 0
    val lst = Iterant[Task]
      .intervalWithFixedDelay(2.seconds)
      .map { e =>
        effect += 1; e
      }
      .take(5)
      .toListL
      .runToFuture

    assertEquals(lst.value, None)
    assertEquals(effect, 1)

    s.tick(2.seconds)
    assertEquals(effect, 2)
    s.tick(2.seconds)
    assertEquals(effect, 3)
    s.tick(2.seconds)
    assertEquals(effect, 4)
    s.tick(2.seconds)
    assertEquals(effect, 5)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L, 3L, 4L))))
  }

  fixture.test("Iterant[IO].intervalWithFixedDelay(2.seconds)") { s =>
    implicit val timer: Timer[IO] = SchedulerEffect.timerLiftIO[IO](s)(IO.ioEffect)

    var effect = 0
    val lst = Iterant[IO]
      .intervalWithFixedDelay(2.seconds)
      .map { e =>
        effect += 1; e
      }
      .take(5)
      .toListL
      .unsafeToFuture()

    assertEquals(lst.value, None)
    assertEquals(effect, 1)

    s.tick(2.seconds)
    assertEquals(effect, 2)
    s.tick(2.seconds)
    assertEquals(effect, 3)
    s.tick(2.seconds)
    assertEquals(effect, 4)
    s.tick(2.seconds)
    assertEquals(effect, 5)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L, 3L, 4L))))
  }

  fixture.test("Iterant[Task].intervalAtFixedRate(1.second)") { implicit s =>
    var effect = 0
    val lst = Iterant[Task]
      .intervalAtFixedRate(1.second)
      .mapEval(e => Task.eval { effect += 1; e }.delayExecution(100.millis))
      .take(3)
      .toListL
      .runToFuture

    assertEquals(lst.value, None)
    assertEquals(effect, 0)
    s.tick(100.millis) // Wait until first effect happens
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(1.second)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L))))
  }

  fixture.test("Iterant[IO].intervalAtFixedRate(1.second)") { s =>
    implicit val timer: Timer[IO] = SchedulerEffect.timerLiftIO[IO](s)(IO.ioEffect)

    var effect = 0
    val lst = Iterant[IO]
      .intervalAtFixedRate(1.second)
      .mapEval(e =>
        timer.sleep(100.millis).map { _ =>
          effect += 1; e
        }
      )
      .take(3)
      .toListL
      .unsafeToFuture()

    assertEquals(lst.value, None)
    assertEquals(effect, 0)
    s.tick(100.millis) // Wait until first effect happens
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(1.second)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L))))
  }

  fixture.test("Iterant[Task].intervalAtFixedRate(2.seconds, 1.second)") { implicit s =>
    var effect = 0
    val lst = Iterant[Task]
      .intervalAtFixedRate(2.seconds, 1.second)
      .mapEval(e => Task.eval { effect += 1; e }.delayExecution(100.millis))
      .take(3)
      .toListL
      .runToFuture

    assertEquals(lst.value, None)
    s.tick(2.seconds)
    assertEquals(effect, 0)

    s.tick(100.millis) // Wait until first effect happens
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(1.second)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L))))
  }

  fixture.test("Iterant[IO].intervalAtFixedRate(2.seconds, 1.second)") { s =>
    implicit val timer: Timer[IO] = SchedulerEffect.timerLiftIO[IO](s)(IO.ioEffect)

    var effect = 0
    val lst = Iterant[IO]
      .intervalAtFixedRate(2.seconds, 1.second)
      .mapEval(e =>
        timer.sleep(100.millis).map { _ =>
          effect += 1; e
        }
      )
      .take(3)
      .toListL
      .unsafeToFuture()

    assertEquals(lst.value, None)
    s.tick(2.seconds)
    assertEquals(effect, 0)

    s.tick(100.millis) // Wait until first effect happens
    assertEquals(effect, 1)

    s.tick(1.second)
    assertEquals(effect, 2)

    s.tick(1.second)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L))))
  }

  fixture.test("Iterant[Task].intervalAtFixedRate accounts for time it takes task to finish") { implicit s =>
    var effect = 0
    val lst = Iterant[Task]
      .intervalAtFixedRate(1.second)
      .mapEval(e => Task.eval { effect += 1; e }.delayExecution(2.seconds))
      .take(3)
      .toListL
      .runToFuture

    assertEquals(lst.value, None)
    assertEquals(effect, 0)

    s.tick(2.seconds)
    assertEquals(effect, 1)

    s.tick(2.seconds)
    assertEquals(effect, 2)

    s.tick(2.seconds)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L))))
  }

  fixture.test("Iterant[IO].intervalAtFixedRate accounts for time it takes task to finish") { s =>
    implicit val timer: Timer[IO] = SchedulerEffect.timerLiftIO[IO](s)(IO.ioEffect)

    var effect = 0
    val lst = Iterant[IO]
      .intervalAtFixedRate(1.second)
      .mapEval(e =>
        timer.sleep(2.seconds).map { _ =>
          effect += 1; e
        }
      )
      .take(3)
      .toListL
      .unsafeToFuture()

    assertEquals(lst.value, None)
    assertEquals(effect, 0)

    s.tick(2.seconds)
    assertEquals(effect, 1)

    s.tick(2.seconds)
    assertEquals(effect, 2)

    s.tick(2.seconds)
    assertEquals(effect, 3)

    assertEquals(lst.value, Some(Success(List(0L, 1L, 2L))))
  }
}
