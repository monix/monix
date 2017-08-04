/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import cats.effect.IO
import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success}

object TaskCatsConversions extends BaseTestSuite {
  test("Task.fromIO(task.toIO) == task") { implicit s =>
    check1 { (task: Task[Int]) =>
      Task.fromIO(task.toIO) <-> task
    }
  }

  test("Task.fromIO(IO.raiseError(e))") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fromIO(IO.raiseError(dummy))
    assertEquals(task.runAsync.value, Some(Failure(dummy)))
  }

  test("Task.fromIO(IO.raiseError(e).shift)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fromIO(for (_ <- IO.shift(s); x <- IO.raiseError[Int](dummy)) yield x)
    val f = task.runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.now(v).toIO") { implicit s =>
    assertEquals(Task.now(10).toIO.unsafeRunSync(), 10)
  }

  test("Task.raiseError(dummy).toIO") { implicit s =>
    val dummy = DummyException("dummy")
    intercept[DummyException] {
      Task.raiseError(dummy).toIO.unsafeRunSync()
    }
  }

  test("Task.eval(thunk).toIO") { implicit s =>
    assertEquals(Task.eval(10).toIO.unsafeRunSync(), 10)
  }

  test("Task.eval(fa).asyncBoundary.toIO") { implicit s =>
    val io = Task.eval(1).asyncBoundary.toIO
    val f = io.unsafeToFuture()

    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.raiseError(dummy).asyncBoundary.toIO") { implicit s =>
    val dummy = DummyException("dummy")
    val io = Task.fork(Task.raiseError[Int](dummy)).toIO
    val f = io.unsafeToFuture()

    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }
}