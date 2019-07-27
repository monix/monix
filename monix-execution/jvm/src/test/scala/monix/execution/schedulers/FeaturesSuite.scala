/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.execution
package schedulers

import minitest.SimpleTestSuite
import scala.concurrent.ExecutionContext.global

object FeaturesSuite extends SimpleTestSuite {
  test("TestScheduler") {
    val ref = TestScheduler()
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(!ref.features.contains(Scheduler.TRACING))
  }

  test("TracingScheduler") {
    val ref = TracingScheduler(global)
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(ref.features.contains(Scheduler.TRACING))
  }

  test("TracingSchedulerService") {
    val ref = TracingSchedulerService(Scheduler.singleThread("features-test"))
    try {
      assert(ref.features.contains(Scheduler.BATCHING))
      assert(ref.features.contains(Scheduler.TRACING))
    } finally {
      ref.shutdown()
    }
  }

  test("Scheduler.Implicits.global") {
    val ref: Scheduler = Scheduler.global
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(!ref.features.contains(Scheduler.TRACING))
  }
}
