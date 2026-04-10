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

package monix.eval

import cats.laws._
import cats.laws.discipline._

import monix.execution.exceptions.DummyException

import scala.util.{ Failure, Success }

object TaskEvalAlwaysSuite extends BaseTestSuite {
  test("Task.eval should work synchronously") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task.eval(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runToFuture
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.eval should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.eval[Int](if (1 == 1) throw ex else 1).runToFuture

    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.lastReportedError, null)
  }

  test("Task.eval is equivalent with Task.evalOnce on first run") { implicit s =>
    check1 { (a: Int) =>
      val t1 = {
        var effect = 100
        Task.eval { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Task.evalOnce { effect += 100; effect + a }
      }

      t1 <-> t2
    }
  }

  test("Task.eval.flatMap should be equivalent with Task.eval") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.eval[Int](if (1 == 1) throw ex else 1).flatMap(Task.now)
    check(t <-> Task.raiseError(ex))
  }

  test("Task.eval.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task.eval(1).flatMap[Int](_ => throw ex)
    check(t <-> Task.raiseError(ex))
  }

  test("Task.eval.flatMap should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Task[Int] =
      Task.eval(idx).flatMap { _ =>
        if (idx < n) loop(n, idx + 1).map(_ + 1)
        else
          Task.eval(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(iterations * 2)))
  }

  test("Task.eval should not be cancelable") { implicit s =>
    val t = Task.eval(10)
    val f = t.runToFuture
    f.cancel()
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.eval.coeval") { implicit s =>
    val result = Task.eval(100).runSyncStep
    assertEquals(result, Right(100))
  }

  test("Task.eval.flatMap should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val task: Task[Int] = Task.eval(1).flatMap(_ => throw ex)
    assertEquals(task.attempt.runSyncStep, Right(Left(ex)))
  }

  test("Task.delay is an alias for Task.eval") { implicit s =>
    var effect = 0
    val ts = Task.delay { effect += 1; effect }

    assertEquals(ts.runToFuture.value, Some(Success(1)))
    assertEquals(ts.runToFuture.value, Some(Success(2)))
    assertEquals(ts.runToFuture.value, Some(Success(3)))

    val dummy = new DummyException("dummy")
    val te = Task.delay { throw dummy }
    assertEquals(te.runToFuture.value, Some(Failure(dummy)))
  }
}
