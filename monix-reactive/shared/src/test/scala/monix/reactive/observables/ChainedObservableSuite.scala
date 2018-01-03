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
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import scala.util.Success

object ChainedObservableSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  test("plain suspend") { implicit s =>
    def loop[A](n: Int): Observable[Int] =
      Observable.suspend {
        if (n > 0) loop(n - 1) else Observable.now(111)
      }

    val f = loop(100000).runAsyncGetLast

    s.tick()
    assertEquals(f.value, Some(Success(Some(111))))
  }

  test("concat") { implicit s =>
    def loop[A](n: Long): Observable[Long] =
      Observable.now(n) ++ Observable.suspend {
        if (n <= 0) Observable.empty
        else if (n == 1) Observable.now(0)
        else loop(n - 1)
      }

    val count = 100000L
    val f = loop(count).sumL.runAsync

    s.tick()
    assertEquals(f.value, Some(Success(count * (count + 1) / 2)))
  }
}
