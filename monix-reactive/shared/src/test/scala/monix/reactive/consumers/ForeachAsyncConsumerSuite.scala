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

package monix.reactive.consumers

import cats.effect.IO
import minitest.TestSuite
import monix.eval.Task
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler
import monix.reactive.{ Consumer, Observable }

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object ForeachAsyncConsumerSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  test("should sum a cats.effect.IO stream") { implicit s =>
    val count = 10000L
    val obs = Observable.range(0, count)
    var sum = 0L
    val f = obs
      .consumeWith(
        Consumer
          .foreachEval(x => IO(sum += x))
      )
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(())))
    assertEquals(sum, count * (count - 1) / 2)
  }

  test("should sum a long stream") { implicit s =>
    val count = 10000L
    val obs = Observable.range(0, count)
    var sum = 0L
    val f = obs
      .consumeWith(
        Consumer
          .foreachTask(x => Task.evalAsync(sum += x))
      )
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(())))
    assertEquals(sum, count * (count - 1) / 2)
  }

  test("should interrupt with error") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable.range(0, 10000).endWithError(ex)
    var sum = 0L
    val f = obs
      .consumeWith(
        Consumer
          .foreachTask(x => Task.evalAsync(sum += x))
      )
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("should protect against user error") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable
      .now(1)
      .consumeWith(Consumer.foreachTask(_ => throw ex))
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("should cancel the last task that started execution") { implicit s =>
    var cancelled = false
    val f = Observable(1)
      .consumeWith(Consumer.foreachTask(_ =>
        Task.never.doOnCancel(Task {
          cancelled = true
        })
      ))
      .runToFuture

    s.tick()
    f.cancel()
    s.tick()
    assert(cancelled)
  }

  test("should suspend effects encountered during the stream") { implicit s =>
    val dummyException = DummyException("Boom!")

    val f: Future[Unit] = {
      Observable(1)
        .consumeWith(
          Consumer.foreachTask(_ =>
            Task.raiseError(dummyException)
          )
        ).runToFuture
    }

    s.tick()
    assertEquals(f.value, Some(Failure(dummyException)))
  }

}
