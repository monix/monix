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

import cats.laws._
import cats.laws.discipline._
import cats.effect.IO
import monix.eval.Task
import monix.execution.exceptions.DummyException
import monix.reactive.{ BaseTestSuite, Consumer, Observable }
import scala.util.{ Failure, Success }

class FoldLeftTaskConsumerSuite extends BaseTestSuite {
  fixture.test("should sum a long stream") { implicit s =>
    val count = 10000L
    val obs = Observable.range(0, count)
    val f = obs
      .consumeWith(
        Consumer
          .foldLeftTask(0L)((s, a) => Task.evalAsync(s + a))
      )
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(count * (count - 1) / 2)))
  }

  fixture.test("should interrupt with error") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable.range(0, 10000).endWithError(ex)
    val f = obs
      .consumeWith(
        Consumer
          .foldLeftTask(0L)((s, a) => Task.evalAsync(s + a))
      )
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("should protect against user simple error") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable
      .now(1)
      .consumeWith(Consumer.foldLeftTask(0L)((s, a) => throw ex))
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("should protect against user task error") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable
      .now(1)
      .consumeWith(Consumer.foldLeftTask(0L)((s, a) => Task.raiseError(ex)))
      .runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("foldLeftTask <-> foldLeftEval") { implicit s =>
    check1 { (source: Observable[Int]) =>
      val fa1 = source.consumeWith(Consumer.foldLeftTask(0L)((s, a) => Task.evalAsync(s + a)))
      val fa2 = source.consumeWith(Consumer.foldLeftEval(0L)((s, a) => IO(s + a)))
      fa1 <-> fa2
    }
  }

  fixture.test("should cancel the last task that started execution") { implicit s =>
    var cancelled = false
    val f = Observable
      .range(1, 5)
      .consumeWith(
        Consumer.foldLeftTask(0L)((_, _) =>
          Task.never.doOnCancel(Task {
            cancelled = true
          })
        )
      )
      .runToFuture

    s.tick()
    f.cancel()
    s.tick()
    assert(cancelled)
  }
}
