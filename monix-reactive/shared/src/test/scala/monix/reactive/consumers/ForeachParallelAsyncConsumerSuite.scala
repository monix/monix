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

package monix.reactive.consumers


import minitest.TestSuite
import monix.eval.{Callback, Task}
import monix.execution.Ack.Continue
import monix.execution.Cancelable
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Consumer, Observable}
import scala.concurrent.Promise
import scala.util.{Failure, Success}

object ForeachParallelAsyncConsumerSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should sum a long stream") { implicit s =>
    val count = 10000L
    val obs = Observable.range(0, count)
    val sum = Atomic(0L)
    val f = obs.consumeWith(Consumer
      .foreachParallelTask(10)(x => Task(sum.add(x))))
      .runAsync

    s.tick()
    assertEquals(f.value, Some(Success(())))
    assertEquals(sum.get, count * (count - 1) / 2)
  }

  test("should interrupt with error") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable.range(0, 10000).endWithError(ex)
    val sum = Atomic(0L)
    val f = obs.consumeWith(Consumer
      .foreachParallelTask(10)(x => Task(sum.add(x))))
      .runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("should protect against user error") { implicit s =>
    val ex = DummyException("dummy")
    var mainWasCanceled = false

    val consumer = Consumer.foreachParallelTask[Int](10)(x => throw ex)
    val onFinish = Promise[Unit]()

    val (out, c) = consumer.createSubscriber(Callback.fromPromise(onFinish), s)
    c := Cancelable { () => mainWasCanceled = true }

    s.tick()
    assertEquals(out.onNext(1), Continue)

    s.tick()
    assert(mainWasCanceled, "mainWasCanceled")
    assertEquals(onFinish.future.value, Some(Failure(ex)))
  }
}
