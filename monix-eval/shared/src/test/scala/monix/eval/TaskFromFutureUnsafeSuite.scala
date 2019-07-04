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

object TaskFromFutureUnsafeSuite extends BaseTestSuite {
  test("Task.fromFutureUnsafe should be faster for completed futures, success") { implicit s =>
    val t = Task.fromFutureUnsafe(Future.successful(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFutureUnsafe should be faster for completed futures, failure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFutureUnsafe(Future.failed(dummy))
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFutureUnsafe should be faster for completed futures, success") { implicit s =>
    val t = Task.fromFutureUnsafe(Future.successful(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFutureUnsafe should work onSuccess") { implicit s =>
    val t = Task.fromFutureUnsafe(Future(10))
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFutureUnsafe should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFutureUnsafe(Future(throw dummy))
    val f = t.runToFuture
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFutureUnsafe should be short-circuited onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFutureUnsafe(p.future)
    p.success(10)
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFutureUnsafe should be short-circuited onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFutureUnsafe(p.future)
    p.failure(dummy)
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFutureUnsafe(cancelable) should work for synchronous results onSuccess") { implicit s =>
    val t = Task.fromFutureUnsafe(CancelableFuture.successful(10))
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFutureUnsafe(cancelable) should work for synchronous results onFailure") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.fromFutureUnsafe(CancelableFuture.failed(dummy))
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFutureUnsafe(cancelable) should be short-circuited onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFutureUnsafe(CancelableFuture(p.future, Cancelable.empty))
    p.success(10)
    val f = t.runToFuture
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFutureUnsafe(cancelable) should be short-circuited onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFutureUnsafe(CancelableFuture(p.future, Cancelable.empty))
    p.failure(dummy)
    val f = t.runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFutureUnsafe(cancelable) should work onSuccess") { implicit s =>
    val p = Promise[Int]()
    val t = Task.fromFutureUnsafe(CancelableFuture(p.future, Cancelable.empty))
    val f = t.runToFuture
    s.tick(); p.success(10); s.tickOne()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.fromFutureUnsafe(cancelable) should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val p = Promise[Int]()
    val t = Task.fromFutureUnsafe(CancelableFuture(p.future, Cancelable.empty))
    val f = t.runToFuture
    s.tick()
    p.failure(dummy)
    s.tickOne()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFutureUnsafe(cancelable) should be cancelable") { implicit s =>
    val source = Task.now(10).delayExecution(1.second)
    val t = Task.fromFutureUnsafe(source.runToFuture)
    val f = t.runToFuture; s.tick()
    assertEquals(f.value, None)
    f.cancel()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.fromFutureUnsafe should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.evalAsync(1).runToFuture
    for (_ <- 0 until count) result = Task.fromFutureUnsafe(result).runToFuture

    assertEquals(result.value, None)
    s.tick()
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.now.fromFutureUnsafe should be stack safe") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 5000
    var result = Task.now(1).runToFuture
    for (_ <- 0 until count) result = Task.fromFutureUnsafe(result).runToFuture
    assertEquals(result.value, Some(Success(1)))
  }

  test("Task.deferFutureUnsafe for already completed references") { implicit s =>
    def sum(list: List[Int]): Task[Int] =
      Task.deferFutureUnsafe(Future.successful(list.sum))

    val f = sum((0 until 100).toList).runToFuture
    assertEquals(f.value, Some(Success(99 * 50)))
  }

  test("Task.deferFutureActionUnsafe for already completed references") { implicit s =>
    def sum(list: List[Int]): Task[Int] =
      Task.deferFutureActionUnsafe(implicit s => Future.successful(list.sum))

    val f = sum((0 until 100).toList).runToFuture
    assertEquals(f.value, Some(Success(99 * 50)))
  }

  test("Task.deferFutureUnsafe(error) for already completed references") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureUnsafe(Future.failed(dummy)).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFutureActionUnsafe(error) for already completed references") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureActionUnsafe(_ => Future.failed(dummy)).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFutureUnsafe for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task.fromFutureUnsafe(Future.successful(acc + 1)).flatMap(loop(n - 1, _))
      else
        Task.fromFutureUnsafe(Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.deferFutureUnsafe for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task.deferFutureUnsafe(Future.successful(acc + 1)).flatMap(loop(n - 1, _))
      else
        Task.deferFutureUnsafe(Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.deferFutureActionUnsafe for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task
          .deferFutureActionUnsafe(implicit s => Future.successful(acc + 1))
          .flatMap(loop(n - 1, _))
      else
        Task.deferFutureActionUnsafe(implicit s => Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.deferFutureUnsafe async result") { implicit s =>
    val f = Task.deferFutureUnsafe(Future(1)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.deferFutureUnsafe(error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureUnsafe(Future(throw dummy)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFutureUnsafe(throw error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureUnsafe(throw dummy).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFutureActionUnsafe async result") { implicit s =>
    val f = Task.deferFutureActionUnsafe(implicit s => Future(1)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.deferFutureActionUnsafe(error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureActionUnsafe(implicit s => Future(throw dummy)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFutureActionUnsafe(throw error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureActionUnsafe(_ => throw dummy).runToFuture
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFutureUnsafe(cancelable)") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.fromFutureUnsafe(f1).runToFuture

    assertEquals(f2.value, None)
    s.tick(1.second)
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.fromFutureUnsafe(cancelable) is cancelable") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.fromFutureUnsafe(f1).runToFuture

    assertEquals(f2.value, None)
    f2.cancel()

    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    s.tick(1.second)
    assertEquals(f2.value, None)
  }

  test("Task.deferFutureUnsafe(cancelable)") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFutureUnsafe(f1).runToFuture

    assertEquals(f2.value, None)
    s.tick(1.second)
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.deferFutureUnsafe(cancelable) is cancelable") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFutureUnsafe(f1).runToFuture

    assertEquals(f2.value, None)
    f2.cancel()

    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    s.tick(1.second)
    assertEquals(f2.value, None)
  }

  test("Task.deferFutureActionUnsafe(cancelable)") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFutureActionUnsafe(implicit s => f1).runToFuture

    assertEquals(f2.value, None)
    s.tick(1.second)
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.deferFutureActionUnsafe(cancelable) is cancelable") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFutureActionUnsafe(implicit s => f1).runToFuture

    assertEquals(f2.value, None)
    f2.cancel()

    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    s.tick(1.second)
    assertEquals(f2.value, None)
  }
}
