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

import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import scala.util.Success

object TaskExecuteAsyncSuite extends BaseTestSuite {
  test("Task.now.executeAsync should execute async") { implicit s =>
    val t = Task.now(10).executeAsync
    val f = t.runToFuture

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.now.executeOn should execute async if forceAsync = true") { implicit s =>
    val s2 = TestScheduler()
    val t = Task.now(10).executeOn(s2)
    val f = t.runToFuture

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, None)
    s2.tick()
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.now.executeOn should not execute async if forceAsync = false") { implicit s =>
    val s2 = TestScheduler()
    val t = Task.now(10).executeOn(s2, forceAsync = false)
    val f = t.runToFuture

    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.create.executeOn should execute async") { implicit s =>
    val s2 = TestScheduler()
    val source = Task.cancelable0[Int] { (_, cb) =>
      cb.onSuccess(10); Task.unit
    }
    val t = source.executeOn(s2)
    val f = t.runToFuture

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, None)
    s2.tick()
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("executeAsync should be stack safe, test 1") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var task = Task.eval(1)
    for (_ <- 0 until count) task = task.executeAsync

    val result = task.runToFuture
    s.tick()
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.executeOn should be stack safe, test 2") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var task = Task.eval(1)
    for (_ <- 0 until count) task = task.executeOn(s)

    val result = task.runToFuture
    s.tick()
    assertEquals(result.value, Some(Success(1)))
  }

  test("executeAsync should be stack safe, test 3") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000

    def loop(n: Int): Task[Int] =
      if (n <= 0) Task.now(0).executeAsync
      else Task.now(n).executeAsync.flatMap(_ => loop(n - 1))

    val result = loop(count).runToFuture
    s.tick()
    assertEquals(result.value, Some(Success(0)))
  }
}
