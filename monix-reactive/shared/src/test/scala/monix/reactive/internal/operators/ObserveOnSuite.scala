/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.reactive.internal.operators

import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.{BaseLawsTestSuite, Observable}

object ObserveOnSuite extends BaseLawsTestSuite {
  test("equivalence with the source") { implicit s =>
    check1 { (obs: Observable[Int]) =>
      obs.observeOn(s) === obs
    }
  }

  test("equivalence with the source when ending in error") { implicit s =>
    check1 { (obs: Observable[Int]) =>
      val dummy = DummyException("dummy")
      val source = obs.endWithError(dummy)
      source.observeOn(s) === source
    }
  }

  test("observes on the specified scheduler, with custom strategy") { implicit s =>
    val count = 4000L
    var signaledBefore = 0L
    var signaledAfter = 0L

    val io = TestScheduler()
    Observable.range(0, count)
      .doOnNext { _ => signaledBefore += 1 }
      .observeOn(io, Unbounded)
      .foreach { _ => signaledAfter += 1 }

    s.tick()
    assertEquals(signaledBefore, count)
    assertEquals(signaledAfter, 0)

    io.tick()
    assertEquals(signaledAfter, count)
  }

  test("observes on the specified scheduler, with default strategy") { implicit s =>
    val count = Platform.recommendedBatchSize - 10
    var signaledBefore = 0L
    var signaledAfter = 0L

    val io = TestScheduler()
    Observable.range(0, count)
      .doOnNext { _ => signaledBefore += 1 }
      .observeOn(io)
      .foreach { _ => signaledAfter += 1 }

    s.tick()
    assertEquals(signaledBefore, count)
    assertEquals(signaledAfter, 0)

    io.tick()
    assertEquals(signaledAfter, count)
  }
}
