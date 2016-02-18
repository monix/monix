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

package monix.streams.internal.builders

import minitest.TestSuite
import monix.execution.Ack.Continue
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.streams.Observable

object RepeatOneObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("first execution is synchronous") { implicit s =>
    var received = 0
    Observable.repeat(1).take(1).foreach(x => received += x)
    assertEquals(received, 1)
  }

  test("should do synchronous execution in batches") { implicit s =>
    var received = 0
    Observable.repeat(1).take(Platform.recommendedBatchSize * 2)
      .subscribe { x => received += 1; Continue }

    assertEquals(received, Platform.recommendedBatchSize)
    s.tickOne()
    assertEquals(received, Platform.recommendedBatchSize * 2)
    s.tickOne()
  }
}
