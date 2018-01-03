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

import cats.laws._
import cats.laws.discipline._
import monix.reactive.{BaseTestSuite, Observable}
import scala.concurrent.duration._

object FirstStartedObservableSuite extends BaseTestSuite {
  test("should mirror the first observable") { implicit s =>
    check2 { (obs1: Observable[Int], obs: Observable[Int]) =>
      val amb = obs1.ambWith(obs.delaySubscription(365.days))
      amb <-> obs1
    }
  }

  test("should mirror the second observable") { implicit s =>
    check2 { (obs: Observable[Int], obs2: Observable[Int]) =>
      val delayed = obs.delaySubscription(365.days)
      val amb = Observable.firstStartedOf(delayed, obs2, delayed, delayed)
      amb <-> obs2
    }
  }

  test("should mirror the third observable") { implicit s =>
    check2 { (obs: Observable[Int], obs3: Observable[Int]) =>
      val delayed = obs.delaySubscription(365.days)
      val amb = Observable.firstStartedOf(delayed, delayed, obs3, delayed)
      amb <-> obs3
    }
  }

  test("first is cancelable") { implicit s =>
    var received = 0

    var obs1Canceled = false
    val obs1 = Observable.intervalAtFixedRate(1.second, 1.second)
      .doOnNext{ _ => received += 1 }
      .doOnSubscriptionCancel { () => obs1Canceled = true }

    var obs2Canceled = false
    val obs2 = Observable.eval(2).delaySubscription(10.second)
      .doOnNext{ _ => received += 1 }
      .doOnSubscriptionCancel { () => obs2Canceled = true }

    val amb = obs1.ambWith(obs2).subscribe()
    assert(!obs1Canceled, "!obs1Canceled")
    assert(!obs2Canceled, "!obs2Canceled")
    assertEquals(received, 0)

    s.tick(1.second)
    assert(!obs1Canceled, "!obs1Canceled")
    assert(obs2Canceled, "obs2Canceled")
    assertEquals(received, 1)

    amb.cancel(); s.tick()
    assert(obs1Canceled, "obs1Canceled")
    assertEquals(received, 1)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("second is cancelable") { implicit s =>
    var received = 0

    var obs1Canceled = false
    val obs1 = Observable.intervalAtFixedRate(10.seconds, 1.second)
      .doOnNext{ _ => received += 1 }
      .doOnSubscriptionCancel { () => obs1Canceled = true }

    var obs2Canceled = false
    val obs2 = Observable.intervalAtFixedRate(1.second, 1.second)
      .doOnNext{ _ => received += 1 }
      .doOnSubscriptionCancel { () => obs2Canceled = true }

    val amb = obs1.ambWith(obs2).subscribe()
    assert(!obs1Canceled, "!obs1Canceled")
    assert(!obs2Canceled, "!obs2Canceled")
    assertEquals(received, 0)

    s.tick(1.second)
    assert(obs1Canceled, "obs1Canceled")
    assert(!obs2Canceled, "!obs2Canceled")
    assertEquals(received, 1)

    amb.cancel(); s.tick()
    assert(obs2Canceled, "obs2Canceled")
    assertEquals(received, 1)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("all are cancelable") { implicit s =>
    var received = 0

    var obs1Canceled = false
    val obs1 = Observable.intervalAtFixedRate(1.seconds, 1.second)
      .doOnNext{ _ => received += 1 }
      .doOnSubscriptionCancel { () => obs1Canceled = true }

    var obs2Canceled = false
    val obs2 = Observable.intervalAtFixedRate(1.second, 1.second)
      .doOnNext{ _ => received += 1 }
      .doOnSubscriptionCancel { () => obs2Canceled = true }

    val amb = obs1.ambWith(obs2).subscribe()
    amb.cancel(); s.tick()

    assert(obs1Canceled, "obs1Canceled")
    assert(obs2Canceled, "obs2Canceled")
    assertEquals(received, 0)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }
}
