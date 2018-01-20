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

import monix.execution.internal.Platform
import monix.reactive.{BaseTestSuite, Observable}

import concurrent.duration._
import scala.util.Success

object UncancelableSuite extends BaseTestSuite {
  test("uncancelable works") { implicit ec =>
    val obs = Observable.eval(1)
      .delaySubscription(1.second)
      .uncancelable

    val f = obs.runAsyncGetFirst
    ec.tick()
    assertEquals(f.value, None)

    f.cancel()
    ec.tick()
    assertEquals(f.value, None)
    assert(ec.state.tasks.nonEmpty, "tasks.nonEmpty")

    ec.tick(1.second)
    assert(ec.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("uncancelable works for suspend loop") { implicit ec =>
    def loop(n: Int): Observable[Int] =
      Observable.suspend {
        if (n > 0)
          loop(n - 1).executeAsync.uncancelable
        else
          Observable.now(1)
      }

    val n = if (Platform.isJVM) 10000 else 1000
    val f = loop(n).runAsyncGetFirst

    f.cancel()
    ec.tick()

    assertEquals(f.value, Some(Success(Some(1))))
  }
}
