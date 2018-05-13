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
import scala.concurrent.duration._

object TaskAsyncSuite extends BaseTestSuite {
  test("Task.never should never complete") { implicit s =>
    val t = Task.never[Int]
    val f = t.runAsync
    s.tick(365.days)
    assertEquals(f.value, None)
  }

  test("Task.async should execute") { implicit s =>
    val task = Task.asyncS[Int] { (ec, cb) =>
      ec.executeAsync { () => cb.onSuccess(1) }
    }

    val f = task.runAsync
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.async should log errors") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.asyncS[Int]((_,_) => throw ex)
    val result = task.runAsync; s.tick()
    assertEquals(result.value, None)
    assertEquals(s.state.lastReportedError, ex)
  }

  test("Task.async should be stack safe") { implicit s =>
    def signal(n: Int) = Task.asyncS[Int]((_, cb) => cb.onSuccess(n))
    def loop(n: Int, acc: Int): Task[Int] =
      signal(1).flatMap { x =>
        if (n > 0) loop(n - 1, acc + x)
        else Task.now(acc)
      }

    val f = loop(10000, 0).runAsync; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }
  
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
      .asyncS[Int]((s, cb) => s.executeAsync(() => cb.onSuccess(1)))
      .runAsync

    sc.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.asyncS works for async error") { implicit sc =>
    val e = DummyException("dummy")
    val f = Task
      .asyncS[Int]((s, cb) => s.executeAsync(() => cb.onError(e)))
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
