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

package monix.reactive.internal.builders

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import monix.reactive.{ Observable, Observer }

object EmptyObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()

  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "Scheduler should be left with no pending tasks")
  }

  test("should complete immediately") { implicit s =>
    var wasCompleted = false
    Observable.empty.unsafeSubscribeFn(new Observer[Any] {
      def onNext(elem: Any) = throw new IllegalStateException()
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = wasCompleted = true
    })

    assert(wasCompleted)
  }
}
