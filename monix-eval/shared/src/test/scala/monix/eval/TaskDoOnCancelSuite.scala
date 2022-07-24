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

import concurrent.duration._
import scala.util.{ Failure, Success }

class TaskDoOnCancelSuite extends BaseTestSuite {
  fixture.test("doOnCancel should normally mirror the source") { implicit s =>
    var effect1 = 0
    var effect2 = 0
    var effect3 = 0

    val f = Task
      .eval(1)
      .delayExecution(1.second)
      .doOnCancel(Task.eval { effect1 += 1 })
      .delayExecution(1.second)
      .doOnCancel(Task.eval { effect2 += 1 })
      .delayExecution(1.second)
      .doOnCancel(Task.eval { effect3 += 1 })
      .runToFuture

    s.tick(3.seconds)
    assertEquals(f.value, Some(Success(1)))
    assertEquals(effect1, 0)
    assertEquals(effect2, 0)
    assertEquals(effect3, 0)
  }

  fixture.test("doOnCancel should mirror failed sources") { implicit s =>
    var effect = 0
    val dummy = new RuntimeException("dummy")
    val f = Task
      .raiseError(dummy)
      .executeAsync
      .doOnCancel(Task.eval { effect += 1 })
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(effect, 0)
  }

  fixture.test("doOnCancel should cancel delayResult #1") { implicit s =>
    var effect1 = 0
    var effect2 = 0
    var effect3 = 0

    val f = Task
      .eval(1)
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect1 += 1 })
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect2 += 1 })
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect3 += 1 })
      .runToFuture

    s.tick(2.seconds)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(effect1, 0)
    assertEquals(effect2, 0)
    assertEquals(effect3, 1)

    assert(s.state.tasks.isEmpty, "s.state.tasks.isEmpty")
  }

  fixture.test("doOnCancel should cancel delayResult #2") { implicit s =>
    var effect1 = 0
    var effect2 = 0
    var effect3 = 0

    val f = Task
      .eval(1)
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect1 += 1 })
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect2 += 1 })
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect3 += 1 })
      .runToFuture

    s.tick(1.seconds)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(effect1, 0)
    assertEquals(effect2, 1)
    assertEquals(effect3, 1)

    assert(s.state.tasks.isEmpty, "s.state.tasks.isEmpty")
  }

  fixture.test("doOnCancel should cancel delayResult #3") { implicit s =>
    var effect1 = 0
    var effect2 = 0
    var effect3 = 0

    val f = Task
      .eval(1)
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect1 += 1 })
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect2 += 1 })
      .delayResult(1.second)
      .doOnCancel(Task.eval { effect3 += 1 })
      .runToFuture

    s.tick()
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(effect1, 1)
    assertEquals(effect2, 1)
    assertEquals(effect3, 1)

    assert(s.state.tasks.isEmpty, "s.state.tasks.isEmpty")
  }

  fixture.test("doOnCancel should cancel delayExecution #1") { implicit s =>
    var effect1 = 0
    var effect2 = 0
    var effect3 = 0

    val f = Task
      .eval(1)
      .doOnCancel(Task.eval { effect1 += 1 })
      .delayExecution(1.second)
      .doOnCancel(Task.eval { effect2 += 1 })
      .delayExecution(1.second)
      .doOnCancel(Task.eval { effect3 += 1 })
      .delayExecution(1.second)
      .runToFuture

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(effect1, 0)
    assertEquals(effect2, 0)
    assertEquals(effect3, 1)

    assert(s.state.tasks.isEmpty, "s.state.tasks.isEmpty")
  }

  fixture.test("doOnCancel should cancel delayExecution #2") { implicit s =>
    var effect1 = 0
    var effect2 = 0
    var effect3 = 0

    val f = Task
      .eval(1)
      .doOnCancel(Task.eval { effect1 += 1 })
      .delayExecution(1.second)
      .doOnCancel(Task.eval { effect2 += 1 })
      .delayExecution(1.second)
      .doOnCancel(Task.eval { effect3 += 1 })
      .delayExecution(1.second)
      .runToFuture

    s.tick(2.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(effect1, 0)
    assertEquals(effect2, 1)
    assertEquals(effect3, 1)

    assert(s.state.tasks.isEmpty, "s.state.tasks.isEmpty")
  }

  fixture.test("doOnCancel is stack safe in flatMap loops") { implicit sc =>
    val onCancel = Task.evalAsync(throw DummyException("dummy"))

    def loop(n: Int, acc: Long): Task[Long] =
      Task.unit.doOnCancel(onCancel).flatMap { _ =>
        if (n > 0)
          loop(n - 1, acc + 1)
        else
          Task.now(acc)
      }

    val f = loop(10000, 0).runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(10000L)))
  }

  fixture.test("local.write.doOnCancel works") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation
    val onCancel = Task.evalAsync(throw DummyException("dummy"))

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).doOnCancel(onCancel)
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }
}
