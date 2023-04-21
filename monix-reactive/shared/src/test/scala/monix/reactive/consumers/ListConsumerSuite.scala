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

import minitest.TestSuite
import monix.execution.CancelableFuture
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler
import monix.reactive.{ Consumer, Observable }

import scala.util.{ Failure, Success }

object ListConsumerSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  test("should return the same all consumed elements as a list") { implicit s =>
    val l = List("a", "b", "c", "d")
    val ob: Observable[String] = Observable.fromIterable(l)
    val f: CancelableFuture[List[String]] = ob.consumeWith(Consumer.toList).runToFuture

    s.tick()
    assertEquals(Some(Success(l)), f.value)
  }

  test("should interrupt with error") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable.range(0, 100).endWithError(ex)
    val f = obs.consumeWith(Consumer.toList).runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

}
