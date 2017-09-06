/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Observable, OverflowStrategy}

import scala.util.Success

object ChainedObservableSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  def testLoop(f: Observable[Int] => Observable[Int])
    (implicit s: TestScheduler): Unit = {

    def loop[A](n: Int, f: Observable[Int] => Observable[Int]): Observable[Int] =
      Observable.suspend {
        if (n > 0) f(loop(n - 1, f))
        else Observable.now(111)
      }

    val f = loop(10000, identity).runAsyncGetLast
    s.tick()
    assertEquals(f.value, Some(Success(Some(111))))
  }

  test("plain suspend") { implicit s =>
    testLoop(identity)
  }

  test("asyncBoundary") { implicit s =>
    testLoop(_.asyncBoundary(OverflowStrategy.Default))
  }

  test("bufferTumbling + map") { implicit s =>
    testLoop(_.bufferTumbling(2).map(_.sum))
  }

  test("filter") { implicit s =>
    testLoop(_.filter(_ % 2 == 1))
  }
}
