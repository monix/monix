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

import monix.execution.exceptions.DummyException

import concurrent.duration._
import scala.util.{ Failure, Success }

class TaskDeferActionSuite extends BaseTestSuite {
  fixture.test("Task.deferAction works") { implicit s =>
    def measureLatency[A](source: Task[A]): Task[(A, Long)] =
      Task.deferAction { implicit s =>
        val start = s.clockMonotonic(MILLISECONDS)
        source.map(a => (a, s.clockMonotonic(MILLISECONDS) - start))
      }

    val task = measureLatency(Task.now("hello").delayExecution(1.second))
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success(("hello", 1000L))))
  }

  fixture.test("Task.deferAction protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.deferAction(_ => throw dummy)
    val f = task.runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  fixture.test("Task.deferAction is stack safe") { implicit sc =>
    def loop(n: Int, acc: Int): Task[Int] =
      Task.deferAction { _ =>
        if (n > 0)
          loop(n - 1, acc + 1)
        else
          Task.now(acc)
      }

    val f = loop(10000, 0).runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("deferAction(local.write) works") {
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val task = for {
      l <- TaskLocal(10)
      _ <- Task.deferAction(_ => l.write(100))
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }
}
