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
import monix.execution.Ack.Continue
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

object StateActionObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("first execution is sync") { implicit s =>
    var received = 0
    Observable.fromStateAction(int)(s.currentTimeMillis())
      .take(1).subscribe { x => received += 1; Continue }
    assertEquals(received, 1)
  }

  test("should do synchronous execution in batches") { implicit s =>
    var received = 0
    Observable.fromStateAction(int)(s.currentTimeMillis())
      .take(Platform.recommendedBatchSize * 2)
      .subscribe { x => received += 1; Continue }

    assertEquals(received, Platform.recommendedBatchSize)
    s.tickOne()
    assertEquals(received, Platform.recommendedBatchSize * 2)
    s.tickOne()
    ()
  }

  test("fromStateAction should be cancelable") { implicit s =>
    var wasCompleted = false
    var sum = 0

    val cancelable = Observable.fromStateAction(int)(s.currentTimeMillis())
      .unsafeSubscribeFn(
        new Subscriber[Int] {
          implicit val scheduler = s
          def onNext(elem: Int) = {
            sum += 1
            Continue
          }

          def onComplete() = wasCompleted = true
          def onError(ex: Throwable) = wasCompleted = true
        })

    cancelable.cancel()
    s.tick()

    assertEquals(sum, s.executionModel.recommendedBatchSize * 2)
    assert(!wasCompleted)
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
