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

import monix.execution.Ack.Continue
import monix.reactive.observers.Subscriber
import monix.reactive.{ BaseTestSuite, Observable }
import scala.concurrent.duration.MILLISECONDS

object PaginateObservableSuite extends BaseTestSuite {

  test("paginate should be exception-proof") { implicit s =>
    val dummy = new RuntimeException("dummy")
    var received = 0

    Observable.paginate(0)(i => if (i < 20) (i, Some(i + 1)) else throw dummy).subscribe { (_: Int) =>
      received += 1
      Continue
    }

    assertEquals((0 until received).toList, (0 to 19).toList)
    assertEquals(s.state.lastReportedError, dummy)
  }

  test("paginate should execute 11 times then return None") { implicit s =>
    var received = 0

    Observable.paginate(0)(i => if (i < 10) (i, Some(i + 1)) else (i, None)).subscribe { (_: Int) =>
      received += 1
      Continue
    }

    assertEquals((0 until received).toList, (0 to 10).toList)
  }

  test("paginate should be cancelable") { implicit s =>
    var wasCompleted = false
    var sum = 0

    val cancelable = Observable
      .paginate(s.clockMonotonic(MILLISECONDS))(intOption)
      .unsafeSubscribeFn(new Subscriber[Int] {
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

  def intOption(seed: Long): (Int, Option[Long]) = {
    // `&` is bitwise AND. We use the current seed to generate a new seed.
    val newSeed = (seed * 0x5deece66dL + 0xbL) & 0xffffffffffffL
    // The next state, which is an `RNG` instance created from the new seed.
    val nextRNG = newSeed
    // `>>>` is right binary shift with zero fill. The value `n` is our new pseudo-random integer.
    val n = (newSeed >>> 16).toInt
    // The return value is a tuple containing both a pseudo-random integer and the next `RNG` state.
    (n, Some(nextRNG))
  }
}
