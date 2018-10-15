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
import monix.eval.Task
import monix.execution.schedulers.TestScheduler
import monix.execution.exceptions.DummyException
import monix.reactive.{Consumer, Observable}

import scala.util.{Failure, Success}

object HeadConsumerSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("stops on first on next") { implicit s =>
    var wasStopped = false
    val obs = Observable.now(1).doOnEarlyStopF { () => wasStopped = true }
    val f = obs.consumeWith(Consumer.head).runToFuture

    s.tick()
    assert(wasStopped, "wasStopped")
    assertEquals(f.value, Some(Success(1)))
  }

  test("on complete") { implicit s =>
    var wasStopped = false
    var wasCompleted = false
    val obs = Observable.empty[Int]
      .doOnEarlyStopF { () => wasStopped = true }
      .doOnCompleteF { () => wasCompleted = true }

    val f = obs.consumeWith(Consumer.head).runToFuture

    s.tick()
    assert(!wasStopped, "!wasStopped")
    assert(wasCompleted, "wasCompleted")
    assert(f.value.isDefined && f.value.get.isFailure &&
      f.value.get.failed.get.isInstanceOf[NoSuchElementException])
  }

  test("on error") { implicit s =>
    val ex = DummyException("dummy")
    var wasStopped = false
    var wasCompleted = false
    val obs = Observable.raiseError(ex)
      .doOnEarlyStopF { () => wasStopped = true }
      .doOnError { _ => Task { wasCompleted = true } }

    val f = obs.consumeWith(Consumer.head).runToFuture

    s.tick()
    assert(!wasStopped, "!wasStopped")
    assert(wasCompleted, "wasStopped")
    assertEquals(f.value, Some(Failure(ex)))
  }
}
