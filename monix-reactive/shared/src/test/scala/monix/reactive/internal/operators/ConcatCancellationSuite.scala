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

package monix.reactive.internal.operators

import monix.eval.Task
import monix.reactive.{BaseLawsTestSuite, Observable}

import scala.concurrent.duration._

/** Tests for cancelling `concat` / `concatMap` and `mapTask`. */
object ConcatCancellationSuite extends BaseLawsTestSuite {
  test("issue #468 - concat is cancellable") { implicit sc =>
    var items = 0

    val a = Observable.now(1L)
    val b = Observable.interval(1.second)
    val c = (a ++ b).doOnNext { _ => items += 1 }
    val d = c.take(10).subscribe()

    assert(items > 0, "items > 0")
    assert(sc.state.tasks.nonEmpty, "tasks.nonEmpty")

    d.cancel()
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("issue #468 - concatMap is cancellable") { implicit sc =>
    val o = Observable.eval(1).executeWithFork.flatMap { x =>
      Observable.now(x).delaySubscription(1.second)
    }

    val c = o.subscribe()
    c.cancel()

    sc.tick()
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("issue #468 - flatScan is cancellable") { implicit sc =>
    val o = Observable.eval(1).executeWithFork.flatScan(0) { (_, x) =>
      Observable.now(x).delaySubscription(1.second)
    }

    val c = o.subscribe()
    c.cancel()

    sc.tick()
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("issue #468 - mapTask is cancellable") { implicit sc =>
    val o = Observable.eval(1).executeWithFork.mapTask { x =>
      Task.now(x).delayExecution(1.second)
    }

    val c = o.subscribe()
    c.cancel()

    sc.tick()
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")
  }
}
