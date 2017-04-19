/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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
import scala.util.{Failure, Success}

object TaskDeferActionSuite extends BaseTestSuite {
  test("Task.deferAction works") { implicit s =>
    def measureLatency[A](source: Task[A]): Task[(A, Long)] =
      Task.deferAction { implicit s =>
        val start = s.currentTimeMillis()
        source.map(a => (a, s.currentTimeMillis() - start))
      }

    val task = measureLatency(Task.now("hello").delayExecution(1.second))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success(("hello", 1000))))
  }

  test("Task.deferAction protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.deferAction(_ => throw dummy)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }
}
