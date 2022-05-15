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

import cats.laws._
import cats.laws.discipline._
import monix.execution.Callback
import monix.execution.atomic.{ Atomic, AtomicInt }
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import scala.util.{ Failure, Random, Success, Try }

object TaskFlatMapSuite extends BaseTestSuite {
  test("runAsync flatMap loop is not cancelable if autoCancelableRunLoops=false") { implicit s =>
    implicit val opts = Task.defaultOptions.disableAutoCancelableRunLoops
    val maxCount = Platform.recommendedBatchSize * 4

    def loop(count: AtomicInt): Task[Unit] =
      if (count.incrementAndGet() >= maxCount) Task.unit
      else
        Task.unit.flatMap(_ => loop(count))

    val atomic = Atomic(0)
    val f = loop(atomic).runToFutureOpt

    f.cancel(); s.tick()
    assertEquals(atomic.get(), maxCount)
    assertEquals(f.value, Some(Success(())))
  }

  test("runAsync flatMap loop is cancelable if ExecutionModel permits") { implicit s =>
    val maxCount = Platform.recommendedBatchSize * 4
    val expected = Platform.recommendedBatchSize

    def loop(count: AtomicInt): Task[Unit] =
      if (count.getAndIncrement() >= maxCount) Task.unit
      else
        Task.unit.flatMap(_ => loop(count))

    val atomic = Atomic(0)
    val f = loop(atomic)
      .executeWithOptions(_.enableAutoCancelableRunLoops)
      .runToFuture

    assertEquals(atomic.get(), expected)
    f.cancel()
    s.tickOne()
    assertEquals(atomic.get(), expected)

    s.tick()
    assertEquals(atomic.get(), expected)
    assertEquals(f.value, None)
  }

  test("runAsync(callback) flatMap loop is cancelable if ExecutionModel permits") { implicit s =>
    val maxCount = Platform.recommendedBatchSize * 4
    val expected = Platform.recommendedBatchSize

    def loop(count: AtomicInt): Task[Unit] =
      if (count.getAndIncrement() >= maxCount) Task.unit
      else
        Task.unit.flatMap(_ => loop(count))

    val atomic = Atomic(0)
    var result = Option.empty[Try[Unit]]

    val c = loop(atomic)
      .executeWithOptions(_.enableAutoCancelableRunLoops)
      .runAsync(new Callback[Throwable, Unit] {
        def onSuccess(value: Unit): Unit =
          result = Some(Success(value))
        def onError(ex: Throwable): Unit =
          result = Some(Failure(ex))
      })

    c.cancel()
    s.tickOne()
    assertEquals(atomic.get(), expected)

    s.tick()
    assertEquals(atomic.get(), expected)
  }

  test("redeemWith derives flatMap") { implicit s =>
    check2 { (fa: Task[Int], f: Int => Task[Int]) =>
      fa.redeemWith(Task.raiseError, f) <-> fa.flatMap(f)
    }
  }

  test("redeemWith derives onErrorHandleWith") { implicit s =>
    check2 { (fa: Task[Int], f: Throwable => Task[Int]) =>
      fa.redeemWith(f, Task.pure) <-> fa.onErrorHandleWith(f)
    }
  }

  test("redeem derives map") { implicit s =>
    check2 { (fa: Task[Int], f: Int => Int) =>
      fa.redeem(e => throw e, f) <-> fa.map(f)
    }
  }

  test("redeem derives onErrorHandle") { implicit s =>
    check2 { (fa: Task[Int], f: Throwable => Int) =>
      fa.redeem(f, x => x) <-> fa.onErrorHandle(f)
    }
  }

  test("redeemWith can recover") { implicit s =>
    val dummy = new DummyException("dummy")
    val task = Task.raiseError[Int](dummy).redeemWith(_ => Task.now(1), Task.now)
    val f = task.runToFuture
    assertEquals(f.value, Some(Success(1)))
  }

  test("redeem can recover") { implicit s =>
    val dummy = new DummyException("dummy")
    val task = Task.raiseError[Int](dummy).redeem(_ => 1, identity)
    val f = task.runToFuture
    assertEquals(f.value, Some(Success(1)))
  }

  test(">> is stack safe for infinite loops") { implicit s =>
    var wasCancelled = false
    def looped: Task[Unit] = Task.cancelBoundary >> looped
    val future = looped.doOnCancel(Task { wasCancelled = true }).runToFuture
    future.cancel()
    s.tick()
    assert(wasCancelled)
  }

  test("flatMapLoop enables loops") { implicit s =>
    val random = Task(Random.nextInt())
    val loop = random.flatMapLoop(Vector.empty[Int]) { (a, list, continue) =>
      val newList = list :+ a
      if (newList.length < 5)
        continue(newList)
      else
        Task.now(newList)
    }
    val f = loop.runToFuture
    s.tick()
    assert(f.value.isDefined)
    assert(f.value.get.isSuccess)
    assertEquals(f.value.get.get.size, 5)
  }

  test("fa *> fb <-> fa.flatMap(_ => fb)") { implicit s =>
    check2 { (fa: Task[Int], fb: Task[Int]) =>
      fa *> fb <-> fa.flatMap(_ => fb)
    }
  }

  test("fa <* fb <-> fa.flatMap(a => fb.map(_ => a))") { implicit s =>
    check2 { (fa: Task[Int], fb: Task[Int]) =>
      fa <* fb <-> fa.flatMap(a => fb.map(_ => a))
    }
  }
}
