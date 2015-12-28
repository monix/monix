/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.internals.builders

import minitest.TestSuite
import monifu.concurrent.schedulers.TestScheduler
import monifu.Ack.Continue
import monifu.Observable

object FromStateActionSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("first execution is async") { implicit s =>
    var received = 0
    Observable.fromStateAction(int)(s.currentTimeMillis())
      .take(1).foreach(x => received += 1)

    assertEquals(received, 0)
    s.tickOne()
    assertEquals(received, 1)
    s.tickOne()
  }

  test("should do synchronous execution in batches") { implicit s =>
    var received = 0
    Observable.fromStateAction(int)(s.currentTimeMillis())
      .take(s.env.batchSize * 2)
      .subscribe { x => received += 1; Continue }

    s.tickOne()
    assertEquals(received, s.env.batchSize)
    s.tickOne()
    assertEquals(received, s.env.batchSize * 2)
    s.tickOne()
  }

  def int(seed: Long): (Int, Long) = {
    // `&` is bitwise AND. We use the current seed to generate a new seed.
    val newSeed = (seed * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL
    // The next state, which is an `RNG` instance created from the new seed.
    val nextRNG = newSeed
    // `>>>` is right binary shift with zero fill. The value `n` is our new pseudo-random integer.
    val n = (newSeed >>> 16).toInt
    // The return value is a tuple containing both a pseudo-random integer and the next `RNG` state.
    (n, nextRNG)
  }
}
