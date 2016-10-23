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

package monix.execution

import minitest.TestSuite
import monix.execution.FutureUtils.extensions._
import monix.execution.schedulers.TestScheduler
import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}

object FutureUtilsSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()

  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")
  }

  test("delayedResult") { implicit s =>
    val f = Future.delayedResult(100.millis)("TICK")

    s.tick(50.millis)
    assert(!f.isCompleted)

    s.tick(100.millis)
    assert(f.value.get.get == "TICK")
  }

  test("withTimeout should succeed") { implicit s =>
    val f = Future.delayedResult(50.millis)("Hello world!")
    val t = f.timeout(300.millis)

    s.tick(10.seconds)
    assert(t.value.get.get == "Hello world!")
  }

  test("withTimeout should fail") { implicit s =>
    val f = Future.delayedResult(1.second)("Hello world!")
    val t = f.timeout(30.millis)

    s.tick(10.seconds)
    intercept[TimeoutException](t.value.get.get)
  }
}
