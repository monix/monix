/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

import monix.execution.Cancelable
import monix.execution.internal.Platform

import scala.util.Success

object TaskForkSuite extends BaseTestSuite {
  test("Task.now.fork should execute async") { implicit s =>
    val t = Task.fork(Task.now(10))
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.evalOnce.fork should execute async") { implicit s =>
    val t = Task.fork(Task.evalOnce(10))
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.evalAlways.fork should execute async") { implicit s =>
    val t = Task.fork(Task.evalAlways(10))
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.defer.fork should execute async") { implicit s =>
    val t = Task.fork(Task.defer(Task.now(10)))
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.async.fork should execute async") { implicit s =>
    val source = Task.unsafeCreate[Int]((s, conn, cb) => cb.onSuccess(10))
    val t = Task.fork(source)
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.async.defer.fork should execute async") { implicit s =>
    val source = Task.unsafeCreate[Int]((s, conn, cb) => cb.onSuccess(10))
    val t = Task.fork(Task.defer(source))
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.async.flatMap.fork should execute async") { implicit s =>
    val source = Task.unsafeCreate[Int]((s, conn, cb) => cb.onSuccess(10)).flatMap(Task.now)
    val t = Task.fork(source)
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.async.memoize.fork should execute async") { implicit s =>
    val source = Task.unsafeCreate[Int]((s, conn, cb) => cb.onSuccess(10))
    val t = Task.fork(source.memoize)
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.create.fork should execute async") { implicit s =>
    val source = Task.create[Int] { (s, cb) => cb.onSuccess(10); Cancelable.empty }
    val t = Task.fork(source)
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fork should be stack safe, test 1") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var task = Task.evalAlways(1)
    for (i <- 0 until count) task = Task.fork(task)

    val result = task.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.executeOn should be stack safe, test 2") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var task = Task.evalAlways(1)
    for (i <- 0 until count) task = task.executeOn(s)

    val result = task.runAsync
    s.tick()
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.fork should be stack safe, test 3") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000

    def loop(n: Int): Task[Int] =
      if (n <= 0) Task.fork(Task.now(0))
      else Task.fork(Task.now(n)).flatMap(_ => loop(n-1))

    val result = loop(count).runAsync
    s.tick()
    assertEquals(result.value, Some(Success(0)))
  }
}
