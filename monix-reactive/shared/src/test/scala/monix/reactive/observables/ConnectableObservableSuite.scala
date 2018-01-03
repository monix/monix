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

package monix.reactive.observables

import minitest.TestSuite
import monix.execution.Ack.Continue
import monix.execution.schedulers.TestScheduler
import monix.reactive.subjects.ConcurrentSubject
import monix.reactive.{Consumer, Observable}

import scala.util.Success

object ConnectableObservableSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  test("should be consumed synchronously with foreach, consumeWith and subscribe") { implicit s =>
    var foreachSum = 0
    var consumerSum = 0
    var subscribeSum = 0

    val observable = Observable.apply(1, 2, 3, 4, 5, 6).publish

    observable.consumeWith(Consumer.foreach(e => consumerSum += e)).runAsync
    observable.foreach(e => foreachSum += e)
    observable.subscribe { e => subscribeSum += e; Continue }

    // Start the streaming
    observable.connect()

    assertEquals(foreachSum, 21)
    assertEquals(consumerSum, 21)
    assertEquals(subscribeSum, 21)
  }

  test("cacheUntilConnect") { implicit s =>
    val source = Observable(1, 2, 3, 4, 5, 6)
    val subject = ConcurrentSubject.replay[Int]
    val observable = ConnectableObservable.cacheUntilConnect(source, subject)

    observable.connect()
    val f = observable.sumL.runAsync

    s.tick()
    assertEquals(f.value, Some(Success(21)))
  }
}