/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TaskFromFutureSuite extends BaseTestSuite {

  test("Task.fromFuture should work onSuccess") { implicit s =>
    val t = Task.fromFuture(Future(10))
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFuture(Future(throw dummy))
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should work onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    val f = t.runToFuture
    s.tick(); p.success(10); s.tickOne()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture(cancelable) should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    val f = t.runToFuture
    s.tick()
    p.failure(dummy)
    s.tickOne()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should be cancelable") { implicit s =>
    val source = Task.now(10).delayExecution(1.second)
    val t = Task.fromFuture(source.runToFuture)
    val f = t.runToFuture; s.tick()
    assertEquals(f.value, None)
    f.cancel()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.fromFuture should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.evalAsync(1).runToFuture
    for (_ <- 0 until count) result = Task.fromFuture(result).runToFuture

    assertEquals(result.value, None)
    s.tick()
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.now.fromFuture should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.now(1).runToFuture
    for (_ <- 0 until count) result = Task.fromFuture(result).runToFuture

    s.tick()
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.fromFuture() should be faster for completed futures, success") { implicit s =>
    val t = Task.fromFuture(Future.successful(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture() should be faster for completed futures, failure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFuture(Future.failed(dummy))
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture() should be faster for completed futures, success") { implicit s =>
    val t = Task.fromFuture(Future.successful(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture() should work onSuccess") { implicit s =>
    val t = Task.fromFuture(Future(10))
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture() should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFuture(Future(throw dummy))
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture() should be short-circuited onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFuture(p.future)
    p.success(10)
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture() should be short-circuited onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFuture(p.future)
    p.failure(dummy)
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should work for synchronous results onSuccess") { implicit s =>
    val t = Task.fromFuture(CancelableFuture.successful(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture(cancelable) should work for synchronous results onFailure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFuture(CancelableFuture.failed(dummy))
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should be short-circuited onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    p.success(10)
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture(cancelable) should be short-circuited onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    p.failure(dummy)
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should work onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    val f = t.runToFuture
    s.tick(); p.success(10); s.tickOne()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFuture(cancelable) should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFuture(CancelableFuture(p.future, Cancelable.empty))
    val f = t.runToFuture
    s.tick()
    p.failure(dummy)
    s.tickOne()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable) should be cancelable") { implicit s =>
    val source = Task.now(10).delayExecution(1.second)
    val t = Task.fromFuture(source.runToFuture)
    val f = t.runToFuture; s.tick()
    assertEquals(f.value, None)
    f.cancel()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.fromFuture() should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.evalAsync(1).runToFuture
    for (_ <- 0 until count) result = Task.fromFuture(result).runToFuture

    assertEquals(result.value, None)
    s.tick()
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.now.fromFuture() should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.now(1).runToFuture
    for (_ <- 0 until count) result = Task.fromFuture(result).runToFuture
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.deferFuture() for already completed references") { implicit s =>
    def sum(list: List[Int]): Task[Int] =
      Task.deferFuture(Future.successful(list.sum))

    val f = sum((0 until 100).toList).runToFuture
    assertEquals(f.value, Some(Success(99 * 50)))
  }

  test("Task.deferFutureAction() for already completed references") { implicit s =>
    def sum(list: List[Int]): Task[Int] =
      Task.deferFutureAction(implicit s => Future.successful(list.sum))

    val f = sum((0 until 100).toList).runToFuture
    assertEquals(f.value, Some(Success(99 * 50)))
  }

  test("Task.deferFuture(error) for already completed references") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFuture(Future.failed(dummy)).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFutureAction(error, ) for already completed references") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureAction(_ => Future.failed(dummy)).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture() for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task.fromFuture(Future.successful(acc + 1)).flatMap(loop(n - 1, _))
      else
        Task.fromFuture(Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.deferFuture() for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task.deferFuture(Future.successful(acc + 1)).flatMap(loop(n - 1, _))
      else
        Task.deferFuture(Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.deferFutureAction() for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task
          .deferFutureAction(implicit s => Future.successful(acc + 1))
          .flatMap(loop(n - 1, _))
      else
        Task.deferFutureAction(implicit s => Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.deferFuture() async result") { implicit s =>
    val f = Task.deferFuture(Future(1)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.deferFuture(error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFuture(Future(throw dummy)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFuture(throw error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFuture(throw dummy).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFutureAction() async result") { implicit s =>
    val f = Task.deferFutureAction(implicit s => Future(1)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.deferFutureAction(error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureAction(implicit s => Future(throw dummy)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFutureAction(throw error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureAction(_ => throw dummy).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture(cancelable)") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.fromFuture(f1).runToFuture

    assertEquals(f2.value, None)
    s.tick(1.second)
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.fromFuture(cancelable) is cancelable") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.fromFuture(f1).runToFuture

    assertEquals(f2.value, None)
    f2.cancel()

    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    s.tick(1.second)
    assertEquals(f2.value, None)
  }

  test("Task.deferFuture(cancelable)") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFuture(f1).runToFuture

    assertEquals(f2.value, None)
    s.tick(1.second)
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.deferFuture(cancelable) is cancelable") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFuture(f1).runToFuture

    assertEquals(f2.value, None)
    f2.cancel()

    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    s.tick(1.second)
    assertEquals(f2.value, None)
  }

  test("Task.deferFutureAction(cancelable)") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFutureAction(implicit s => f1).runToFuture

    assertEquals(f2.value, None)
    s.tick(1.second)
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.deferFutureAction(cancelable) is cancelable") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFutureAction(implicit s => f1).runToFuture

    assertEquals(f2.value, None)
    f2.cancel()

    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    s.tick(1.second)
    assertEquals(f2.value, None)
  }
}
