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

import monix.execution.exceptions.DummyException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object TaskToFutureSuite extends BaseTestSuite {
  test("Task.fromFuture for already completed references") { implicit s =>
    def sum(list: List[Int]): Task[Int] =
      Task.fromFuture(Future.successful(list.sum))

    val f = sum((0 until 100).toList).runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(99 * 50)))
  }

  test("Task.deferFuture for already completed references") { implicit s =>
    def sum(list: List[Int]): Task[Int] =
      Task.deferFuture(Future.successful(list.sum))

    val f = sum((0 until 100).toList).runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(99 * 50)))
  }

  test("Task.deferFutureAction for already completed references") { implicit s =>
    def sum(list: List[Int]): Task[Int] =
      Task.deferFutureAction(_ => Future.successful(list.sum))

    val f = sum((0 until 100).toList).runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(99 * 50)))
  }

  test("Task.fromFuture(error) for already completed references") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.fromFuture(Future.failed(dummy)).runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFuture(error) for already completed references") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFuture(Future.failed(dummy)).runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFutureAction(error) for already completed references") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.deferFutureAction(_ => Future.failed(dummy)).runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.fromFuture for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task.fromFuture(Future.successful(acc + 1)).flatMap(loop(n - 1, _))
      else
        Task.fromFuture(Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.deferFuture for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task.deferFuture(Future.successful(acc + 1)).flatMap(loop(n - 1, _))
      else
        Task.deferFuture(Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.deferFutureAction for completed reference is stack safe (flatMap)") { implicit s =>
    def loop(n: Int, acc: Int): Task[Int] =
      if (n > 0)
        Task
          .deferFutureAction(_ => Future.successful(acc + 1))
          .flatMap(loop(n - 1, _))
      else
        Task.deferFutureAction(_ => Future.successful(acc))

    val f = loop(10000, 0).runToFuture; s.tick()
    assertEquals(f.value, Some(Success(10000)))
  }

  test("Task.fromFuture async result") { implicit s =>
    val f = Task.fromFuture(Future(1)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.fromFuture(error) async result") { implicit s =>
    val dummy = DummyException("dummy")
    val f = Task.fromFuture(Future(throw dummy)).runToFuture
    assertEquals(f.value, None)

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.deferFuture async result") { implicit s =>
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

  test("Task.deferFutureAction async result") { implicit s =>
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
    val f2 = Task.deferFutureAction(_ => f1).runToFuture

    assertEquals(f2.value, None)
    s.tick(1.second)
    assertEquals(f2.value, Some(Success(1)))
  }

  test("Task.deferFutureAction(cancelable) is cancelable") { implicit s =>
    val f1 = Task.eval(1).delayExecution(1.second).runToFuture
    val f2 = Task.deferFutureAction(_ => f1).runToFuture

    assertEquals(f2.value, None)
    f2.cancel()

    s.tick()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    s.tick(1.second)
    assertEquals(f2.value, None)
  }
}
