/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
import monix.eval.Task
import monix.execution.atomic.AtomicLong
import monix.execution.exceptions.{CompositeException, DummyException}
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Consumer, Observable, OverflowStrategy}

import scala.util.{Failure, Success}

object PartitionParallelConsumerSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }
  test("should trigger error") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable
      .raiseError(ex)
      .consumeWith(Consumer.partitionParallel[Long](3, OverflowStrategy.Unbounded, _.toInt, (_,_) => Task.unit)).runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("should deal with consumer errors") { implicit s =>

    val ex = DummyException("consumer error")
    val f = Observable(1l, 2l, 3l)
      .consumeWith(Consumer.partitionParallel[Long](3, OverflowStrategy.Unbounded, _.toInt, (_,_) => Task.raiseError(ex))).runAsync

    s.tick()

    f.value.foreach {
      case scala.util.Failure(ex) => println(ex)
      case scala.util.Success(v) => println(v)
    }

    assertEquals(f.value, Some(Failure(CompositeException(Seq(ex, ex, ex)))))
  }


  test("should consume all") { implicit s =>
    val counter = AtomicLong(0l)
    val count = 100000l

    val f = Observable.range(0, count)
        .consumeWith(Consumer.partitionParallel[Long](10, OverflowStrategy.Unbounded, _.toInt, (_, seq) => Task(counter.increment(seq.size)))).runAsync

    s.tick()

    assertEquals(counter.get, count)
  }

  test("should complete") { implicit s =>

    val f = Observable.range(0, 100000l)
      .consumeWith(Consumer.partitionParallel[Long](10, OverflowStrategy.Unbounded, _.toInt, (_, seq) => Task.unit)).runAsync

    s.tick()

    assertEquals(f.value, Some(Success(())))
  }
}
