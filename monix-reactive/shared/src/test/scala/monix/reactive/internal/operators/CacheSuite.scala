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

import monix.reactive.Observable

import scala.concurrent.duration.Duration.Zero
import scala.util.Success

object CacheSuite extends BaseOperatorSuite {
  def createObservable(c: Int) = Some {
    val o = Observable.range(0, c).cache
    Sample(o, c, c * (c - 1) / 2, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def cancelableObservables() = Seq.empty

  test("should work with limited capacity") { implicit s =>
    val observable = Observable(1, 2, 3, 4, 5, 6).cache(2)
    val f = observable.sumL.runAsync

    s.tick()
    assertEquals(f.value, Some(Success(11)))
  }

  test("should require capacity > 0") { implicit s =>
    intercept[IllegalArgumentException]{
      Observable.empty[Int].cache(0)
    }
  }
}