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

import scala.concurrent.duration._

object ThrottleFirstSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    if (sourceCount == 1) {
      val o = Observable.now(100L).delaySubscription(500.millis).throttleFirst(1.second)
      Sample(o, 1, 100, 500.millis, 1.second)
    }
    else {
      val div2 = sourceCount / 2 * 2
      val o = Observable.intervalAtFixedRate(500.millis)
        .take(div2)
        .throttleFirst(1.second)

      val count = div2 / 2
      Sample(o, count, count * (count - 1), Duration.Zero, 1.second)
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
