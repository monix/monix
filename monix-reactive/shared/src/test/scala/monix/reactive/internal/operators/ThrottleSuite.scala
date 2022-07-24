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

package monix.reactive.internal.operators

import monix.reactive.Observable

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.util.Success

class ThrottleSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int) = {
    sourceCount * (sourceCount - 1) / 2
  }

  def createObservable(sourceCount: Int) = Some {
    require(sourceCount > 0, "sourceCount must be strictly positive")

    val o = Observable
      .range(0L, sourceCount.toLong)
      .throttle(1.second, 2)

    Sample(o, sourceCount, sum(sourceCount), 1.second, 1.second)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val o = Observable.intervalAtFixedRate(500.millis).throttle(1.second, 2)
    Seq(
      Sample(o, 0, 0, 0.seconds, 0.seconds),
      Sample(o, 2, 3, 1.seconds, 1.seconds)
    )
  }

  fixture.test("should emit elements at given rate without dropping them") { implicit s =>
    val lastElements = ListBuffer[Long]()

    val f = Observable
      .range(0, 10)
      .throttle(1.second, 2)
      .map(lastElements += _)
      .completedL
      .runToFuture

    s.tick(1.seconds)
    assertEquals(lastElements.toList, List(0L, 1))
    s.tick(2.seconds)
    assertEquals(lastElements.toList, List(0L, 1, 2, 3, 4, 5))
    s.tick(1.day)
    assertEquals(lastElements.toList, List(0L, 1, 2, 3, 4, 5, 6, 7, 8, 9))
    assertEquals(f.value, Some(Success(())))
  }
}
