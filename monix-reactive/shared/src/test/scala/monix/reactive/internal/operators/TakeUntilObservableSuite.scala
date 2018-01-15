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

import cats.laws._
import cats.laws.discipline._

import monix.reactive.Observable

import concurrent.duration._
import scala.util.Success

object TakeUntilObservableSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val source = Observable.intervalAtFixedRate(2.seconds, 2.seconds)
    val trigger = Observable.now(1).delaySubscription(2.seconds * sourceCount + 1.second)
    val obs = source.takeUntil(trigger)

    Sample(obs, sourceCount, sourceCount * (sourceCount-1) / 2, 2.seconds, 2.seconds)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val source = Observable.intervalAtFixedRate(2.seconds, 2.seconds)
    val trigger = Observable.raiseError(ex).delaySubscription(2.seconds * sourceCount + 1.second)
    val obs = source.takeUntil(trigger)

    Sample(obs, sourceCount, sourceCount * (sourceCount-1) / 2, 2.seconds, 2.seconds)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  def cancelableObservables() = {
    val source = Observable.intervalAtFixedRate(2.seconds, 2.seconds)
    val trigger = Observable.now(1).delaySubscription(2.seconds * 10)
    val obs = source.takeUntil(trigger)

    Seq(
      Sample(obs, 0, 0, 0.seconds, 0.seconds),
      Sample(obs, 1, 0, 2.seconds, 0.seconds),
      Sample(obs, 2, 1, 4.seconds, 0.seconds),
      Sample(obs, 3, 3, 6.seconds, 0.seconds)
    )
  }

  test("should mirror the source if never triggered") { implicit s =>
    check1 { (obs: Observable[Int]) =>
      obs <-> obs.takeUntil(Observable.never)
    }
  }

  test("should cancel the trigger if finished before it") { implicit s =>
    val obs = Observable(1).executeAsync.takeUntil(Observable.now(1).delaySubscription(1.second))
    val f = obs.runAsyncGetFirst

    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    s.tick()

    assertEquals(f.value, Some(Success(Some(1))))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }
}
