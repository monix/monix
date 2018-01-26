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

import cats.laws._
import cats.laws.discipline._

import monix.execution.atomic.{Atomic, AtomicInt}
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

import scala.util.{Failure, Success, Try}

object TaskFlatMapSuite extends BaseTestSuite {
  test("runAsync flatMap loop is not cancelable if autoCancelableRunLoops=false") { implicit s =>
    val maxCount = Platform.recommendedBatchSize * 4

    def loop(count: AtomicInt): Task[Unit] =
      if (count.incrementAndGet() >= maxCount) Task.unit else
        Task.unit.flatMap(_ => loop(count))

    val atomic = Atomic(0)
    val f = loop(atomic)
      .executeWithOptions(_.disableAutoCancelableRunLoops)
      .runAsync

    f.cancel(); s.tick()
    assertEquals(atomic.get, maxCount)
    assertEquals(f.value, Some(Success(())))
  }

  test("runAsync flatMap loop is cancelable if ExecutionModel permits") { implicit s =>
    val maxCount = Platform.recommendedBatchSize * 4
    val expected = Platform.recommendedBatchSize

    def loop(count: AtomicInt): Task[Unit] =
      if (count.getAndIncrement() >= maxCount) Task.unit else
        Task.unit.flatMap(_ => loop(count))

    val atomic = Atomic(0)
    val f = loop(atomic)
      .cancelable
      .runAsync

    assertEquals(atomic.get, expected)
    f.cancel()
    s.tickOne()
    assertEquals(atomic.get, expected)

    s.tick()
    assertEquals(atomic.get, expected)
    assertEquals(f.value, None)
  }

  test("runAsync(callback) flatMap loop is cancelable if ExecutionModel permits") { implicit s =>
    val maxCount = Platform.recommendedBatchSize * 4
    val expected = Platform.recommendedBatchSize

    def loop(count: AtomicInt): Task[Unit] =
      if (count.getAndIncrement() >= maxCount) Task.unit else
        Task.unit.flatMap(_ => loop(count))

    val atomic = Atomic(0)
    var result = Option.empty[Try[Unit]]

    val c = loop(atomic)
      .cancelable
      .runAsync(new Callback[Unit] {
        def onSuccess(value: Unit): Unit =
          result = Some(Success(value))
        def onError(ex: Throwable): Unit =
          result = Some(Failure(ex))
      })

    c.cancel()
    s.tickOne()
    assertEquals(atomic.get, expected)

    s.tick()
    assertEquals(atomic.get, expected)
  }

  test("transformWith equivalence with flatMap") { implicit s =>
    check2 { (fa: Task[Int], f: Int => Task[Int]) =>
      fa.transformWith(f, Task.raiseError) <-> fa.flatMap(f)
    }
  }

  test("transform equivalence with map") { implicit s =>
    check2 { (fa: Task[Int], f: Int => Int) =>
      fa.transform(f, ex => throw ex) <-> fa.map(f)
    }
  }

  test("transformWith can recover") { implicit s =>
    val dummy = new DummyException("dummy")
    val task = Task.raiseError[Int](dummy).transformWith(Task.now, _ => Task.now(1))
    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }

  test("transform can recover") { implicit s =>
    val dummy = new DummyException("dummy")
    val task = Task.raiseError[Int](dummy).transform(identity, _ => 1)
    val f = task.runAsync
    assertEquals(f.value, Some(Success(1)))
  }
}
