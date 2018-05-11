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

import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success}

object TaskAsyncSuite extends BaseTestSuite {
  test("Task.async works for immediate successful value") { implicit sc =>
    val task = Task.async[Int](_.onSuccess(1))
    assertEquals(task.runAsync.value, Some(Success(1)))
  }

  test("Task.async works for immediate error") { implicit sc =>
    val e = DummyException("dummy")
    val task = Task.async[Int](_.onError(e))
    assertEquals(task.runAsync.value, Some(Failure(e)))
  }

  test("Task.async is memory safe in flatMap loops") { implicit sc =>
    def signal(n: Int): Task[Int] = Task.async(_.onSuccess(n))

    def loop(n: Int, acc: Int): Task[Int] =
      signal(n).flatMap { n =>
        if (n > 0) loop(n - 1, acc + 1)
        else Task.now(acc)
      }

    val f = loop(10000, 0).runAsync; sc.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.asyncS works for immediate successful value") { implicit sc =>
    val task = Task.asyncS[Int]((_, cb) => cb.onSuccess(1))
    assertEquals(task.runAsync.value, Some(Success(1)))
  }

  test("Task.asyncS works for async successful value") { implicit sc =>
    val f = Task
      .asyncS[Int]((s, cb) => s.execute(() => cb.onSuccess(1)))
      .runAsync

    sc.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.asyncS works for async error") { implicit sc =>
    val e = DummyException("dummy")
    val f = Task
      .asyncS[Int]((s, cb) => s.execute(() => cb.onError(e)))
      .runAsync

    sc.tick()
    assertEquals(f.value, Some(Failure(e)))
  }

  test("Task.asyncS is memory safe in synchronous flatMap loops") { implicit sc =>
    def signal(n: Int): Task[Int] = Task.asyncS((_, cb) => cb.onSuccess(n))

    def loop(n: Int, acc: Int): Task[Int] =
      signal(n).flatMap { n =>
        if (n > 0) loop(n - 1, acc + 1)
        else Task.now(acc)
      }

    val f = loop(10000, 0).runAsync; sc.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.asyncS is memory safe in async flatMap loops") { implicit sc =>
    def signal(n: Int): Task[Int] =
      Task.asyncS((s, cb) => s.executeAsync(() => cb.onSuccess(n)))

    def loop(n: Int, acc: Int): Task[Int] =
      signal(n).flatMap { n =>
        if (n > 0) loop(n - 1, acc + 1)
        else Task.now(acc)
      }

    val f = loop(10000, 0).runAsync; sc.tick()
    assertEquals(f.value, Some(Success(10000)))
  }
}
