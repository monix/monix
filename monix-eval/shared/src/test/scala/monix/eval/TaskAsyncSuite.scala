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

import monix.execution.{Cancelable, CancelableFuture}
import concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

object TaskAsyncSuite extends BaseTestSuite {
  test("Task.never should never complete") { implicit s =>
    val t = Task.never[Int]
    val f = t.runAsync
    s.tick(365.days)
    assertEquals(f.value, None)
  }

  test("Task.fromFuture should be faster for completed futures, success") { implicit s =>
    val t = Task.fromFuture(Future.successful(10))
    val f = t.runAsync
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture should be faster for completed futures, failure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFuture(Future.failed(dummy))
    val f = t.runAsync
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture should be faster for completed futures, success") { implicit s =>
    val t = Task.fromFuture(Future.successful(10))
    val f = t.runAsync
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture should work onSuccess") { implicit s =>
    val t = Task.fromFuture(Future(10))
    val f = t.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFuture(Future(throw dummy))
    val f = t.runAsync
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture should be short-circuited onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFuture(p.future)
    p.success(10)
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tickOne()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture should be short-circuited onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFuture(p.future)
    p.failure(dummy)
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tickOne()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should work for synchronous results onSuccess") { implicit s =>
    val t = Task.fromFuture(CancelableFuture.successful(10))
    val f = t.runAsync
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture(cancelable) should work for synchronous results onFailure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFuture(CancelableFuture.failed(dummy))
    val f = t.runAsync
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should be short-circuited onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    p.success(10)
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tickOne()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture(cancelable) should be short-circuited onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    p.failure(dummy)
    val f = t.runAsync
    assertEquals(f.value, None)
    s.tickOne()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should work onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    val f = t.runAsync
    s.tick(); p.success(10); s.tickOne()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture(cancelable) should be onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    val f = t.runAsync
    s.tick(); p.failure(dummy); s.tickOne()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should be cancelable") { implicit s =>
    val source = Task.now(10).delayExecution(1.second)
    val t = Task.fromFuture(source.runAsync)
    val f = t.runAsync; s.tick()
    assertEquals(f.value, None)
    f.cancel()
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.create should work onSuccess") { implicit s =>
    val t = Task.create[Int] { (s,cb) => cb.onSuccess(10); Cancelable.empty }
    val f = t.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.create should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.create[Int] { (s,cb) => cb.onError(dummy); Cancelable.empty }
    val f = t.runAsync
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }
}
