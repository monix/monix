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

package monix.reactive.internal.builders

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Observer, Observable}
import monix.execution.exceptions.DummyException

object ErrorObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "Scheduler should be left with no pending tasks")
  }

  test("should stream immediately") { implicit s =>
    var errorThrown: Throwable = null
    Observable.raiseError(DummyException("dummy")).unsafeSubscribeFn(new Observer[Any] {
      def onError(ex: Throwable): Unit = errorThrown = ex
      def onNext(elem: Any) = throw new IllegalStateException()
      def onComplete(): Unit = throw new IllegalStateException()
    })

    assertEquals(errorThrown, DummyException("dummy"))
  }
}
