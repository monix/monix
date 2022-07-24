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

import monix.execution.atomic.AtomicInt
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class TaskParSequenceNSuite extends BaseTestSuite {

  fixture.test("Task.parSequenceN should execute in parallel bounded by parallelism") { implicit s =>
    val num = AtomicInt(0)
    val task = Task.evalAsync(num.increment()) >> Task.sleep(2.seconds)
    val seq = List.fill(100)(task)

    Task.parSequenceN(5)(seq).runToFuture

    s.tick()
    assertEquals(num.get(), 5)
    s.tick(2.seconds)
    assertEquals(num.get(), 10)
    s.tick(4.seconds)
    assertEquals(num.get(), 20)
    s.tick(34.seconds)
    assertEquals(num.get(), 100)
  }

  fixture.test("Task.parSequenceN should return result in order") { implicit s =>
    val task = 1.until(10).toList.map(Task.eval(_))
    val res = Task.parSequenceN(2)(task).runToFuture

    s.tick()
    assertEquals(res.value, Some(Success(List(1, 2, 3, 4, 5, 6, 7, 8, 9))))
  }

  fixture.test("Task.parSequenceN should return empty list") { implicit s =>
    val res = Task.parSequenceN(2)(List.empty).runToFuture

    s.tick()
    assertEquals(res.value, Some(Success(List.empty)))
  }

  fixture.test("Task.parSequenceN should handle single item") { implicit s =>
    val task = List(Task.eval(1))
    val res = Task.parSequenceN(2)(task).runToFuture

    s.tick()
    assertEquals(res.value, Some(Success(List(1))))
  }

  fixture.test("Task.parSequenceN should handle parallelism bigger than list") { implicit s =>
    val task = 1.until(5).toList.map(Task.eval(_))
    val res = Task.parSequenceN(10)(task).runToFuture

    s.tick()
    assertEquals(res.value, Some(Success(List(1, 2, 3, 4))))
  }

  fixture.test("Task.parSequenceN should onError if one of the tasks terminates in error") { implicit s =>
    val ex = DummyException("dummy")
    val seq = Seq(
      Task.evalAsync(3).delayExecution(2.seconds),
      Task.evalAsync(2).delayExecution(1.second),
      Task.evalAsync(throw ex).delayExecution(2.seconds),
      Task.evalAsync(3).delayExecution(1.seconds)
    )

    val f = Task.parSequenceN(2)(seq).runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("Task.parSequenceN should be canceled") { implicit s =>
    val num = AtomicInt(0)
    val seq = Seq(
      Task.unit.delayExecution(3.seconds).doOnCancel(Task.eval(num.increment())),
      Task.evalAsync(num.increment(10))
    )
    val f = Task.parSequenceN(1)(seq).runToFuture

    s.tick(2.seconds)
    f.cancel()
    assertEquals(num.get(), 1)

    s.tick(1.day)
    assertEquals(num.get(), 1)
  }

  fixture.test("Task.parSequenceN should be stack safe for synchronous tasks") { implicit s =>
    val count = if (Platform.isJVM) 200000 else 5000
    val tasks = for (_ <- 0 until count) yield Task.now(1)
    val composite = Task.parSequenceN(count)(tasks).map(_.sum)
    val result = composite.runToFuture
    s.tick()
    assertEquals(result.value, Some(Success(count)))
  }

  fixture.test("Task.parSequenceN runAsync multiple times") { implicit s =>
    var effect = 0
    val task1 = Task.evalAsync { effect += 1; 3 }.memoize
    val task2 = task1.map { x =>
      effect += 1; x + 1
    }
    val task3 = Task.parSequenceN(2)(List(task2, task2, task2))

    val result1 = task3.runToFuture; s.tick()
    assertEquals(result1.value, Some(Success(List(4, 4, 4))))
    assertEquals(effect, 1 + 3)

    val result2 = task3.runToFuture; s.tick()
    assertEquals(result2.value, Some(Success(List(4, 4, 4))))
    assertEquals(effect, 1 + 3 + 3)
  }
}
