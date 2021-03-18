/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import scala.concurrent.duration._

object ThrottleLatestSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int) = {
    sourceCount * (sourceCount - 1) / 2
  }
  def createObservable(sourceCount: Int) = Some {
    if (sourceCount == 1) {
      val o = Observable.now(100L).delayExecution(500.millis).throttleLatest(1.second, true)
      Sample(o, 1, 100, 500.millis, 1.second)
    } else {

      val o = Observable
        .intervalAtFixedRate(1.second)
        .take(sourceCount.toLong)
        .throttleLatest(1.second, true)

      Sample(o, sourceCount, sum(sourceCount), 1.second, 1.second)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val o = Observable.intervalAtFixedRate(500.millis, 1.second).throttleFirst(1.second)
    Seq(
      Sample(o, 0, 0, 0.seconds, 0.seconds),
      Sample(o, 1, 1, 1.seconds, 0.seconds)
    )
  }
}
