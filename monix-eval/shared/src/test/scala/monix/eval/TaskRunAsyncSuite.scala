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

import scala.concurrent.Promise
import scala.util.Success
import scala.concurrent.duration._

object TaskRunAsyncSuite extends BaseTestSuite {
  test("runAsync") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1)
    val f = task.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(4)))
  }

  test("runAsync is cancelable") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).delayExecution(1.second)
    val f = task.runAsync
    s.tick()

    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(f.value, None)
    f.cancel(); s.tick()

    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(f.value, None)
  }

  test("runAsync(Callback)") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1)
    val p = Promise[Int]()
    task.runAsync(Callback.fromPromise(p))
    s.tick()
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("runAsync(Callback) is cancelable") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).delayExecution(1.second)
    val p = Promise[Int]()
    val f = task.runAsync(Callback.fromPromise(p))
    s.tick()

    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(p.future.value, None)
    f.cancel(); s.tick()

    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(p.future.value, None)
  }

  test("runAsyncF(Callback)") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1)
    val p = Promise[Int]()
    task.runAsyncF(Callback.fromPromise(p))
    s.tick()
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("runAsyncF(Callback) is cancelable") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).delayExecution(1.second)
    val p = Promise[Int]()
    val f = task.runAsyncF(Callback.fromPromise(p))
    s.tick()

    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(p.future.value, None)
    f.runAsyncAndForget; s.tick()

    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(p.future.value, None)
  }

  test("runAsyncUncancelable(Callback)") { implicit s =>
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1)
    val p = Promise[Int]()
    task.runAsyncUncancelable(Callback.fromPromise(p))
    s.tick()
    assertEquals(p.future.value, Some(Success(4)))
  }

  test("runAsyncAndForget") { implicit s =>
    var effect = 0
    val task = Task(1).flatMap(x => Task(x + 2)).executeAsync.map(_ + 1).foreachL { i => effect = i }
    task.runAsyncAndForget
    s.tick()
    assertEquals(effect, 4)
  }
}
