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

package monix.eval

import cats.laws._
import cats.laws.discipline._

import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success}

object TaskApplySuite extends BaseTestSuite {
  test("Task.apply should work, on different thread") { implicit s =>
    var wasTriggered = false
    def trigger(): String = { wasTriggered = true; "result" }

    val task = Task(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.apply should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](if (1 == 1) throw ex else 1).runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.lastReportedError, null)
  }

  test("Task.apply is equivalent with Task.eval") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Task { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Task.eval { effect += 100; effect + a }
      }

      List(t1 <-> t2, t2 <-> t1)
    }
  }

  test("Task.apply is equivalent with Task.evalOnce on first run") { implicit s =>
    check1 { a: Int =>
      val t1 = {
        var effect = 100
        Task { effect += 100; effect + a }
      }

      val t2 = {
        var effect = 100
        Task.evalOnce { effect += 100; effect + a }
      }

      t1 <-> t2
    }
  }

  test("Task.apply.flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    val t = Task(1).flatMap[Int](_ => throw ex)
    check(t <-> Task.raiseError(ex))
  }

  test("Task.apply should be tail recursive") { implicit s =>
    def loop(n: Int, idx: Int): Task[Int] =
      Task.apply(idx).flatMap { idx =>
        if (idx < n) loop(n, idx + 1).map(_ + 1) else
          Task.apply(idx)
      }

    val iterations = s.executionModel.recommendedBatchSize * 20
    val f = loop(iterations, 0).runAsync

    s.tick()
    assertEquals(f.value, Some(Success(iterations * 2)))
  }

  test("Task.apply.flatten is equivalent with flatMap") { implicit s =>
    check1 { a: Int =>
      val t = Task(Task.eval(a))
      t.flatMap(identity) <-> t.flatten
    }
  }

  test("Task.apply.coeval") { implicit s =>
    Task(100).coeval.value match {
      case Left(result) =>
        assertEquals(result.value, None)
        s.tick()
        assertEquals(result.value, Some(Success(100)))
      case r @ Right(_) =>
        fail(s"Received incorrect result: $r")
    }
  }
}
