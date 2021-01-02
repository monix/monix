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

package monix.eval

import monix.execution.exceptions.DummyException

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TaskTimedSuite extends BaseTestSuite {

  test("Task.timed works for successful tasks") { implicit s =>
    val task = Task.now("hello").delayExecution(2.second).timed
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success(2.second -> "hello")))

    s.tick(1.second)
    assertEquals(f.value, Some(Success(2.second -> "hello")))
  }

  test("Task.timed doesn't time failed tasks") { implicit s =>
    val task = Task.raiseError(DummyException("dummy")).delayExecution(2.second).timed
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(DummyException("dummy"))))

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(DummyException("dummy"))))
  }

  test("Task.timed could time errors if .attempt is called first") { implicit s =>
    val task = Task.raiseError(DummyException("dummy")).delayExecution(2.second).attempt.timed
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success(2.second -> Left(DummyException("dummy")))))

    s.tick(1.second)
    assertEquals(f.value, Some(Success(2.second -> Left(DummyException("dummy")))))
  }

  test("Task.timed is stack safe") { implicit sc =>
    def loop(n: Int, acc: Duration): Task[Duration] =
      Task.unit.delayResult(1.second).timed.flatMap {
        case (duration, _) =>
          if (n > 0)
            loop(n - 1, acc + duration)
          else
            Task.now(acc)
      }

    val f = loop(10000, 0.second).runToFuture; sc.tick(10001.seconds)
    assertEquals(f.value, Some(Success(10000.seconds)))
  }

}
