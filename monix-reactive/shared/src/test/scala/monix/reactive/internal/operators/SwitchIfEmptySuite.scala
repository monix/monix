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
import scala.concurrent.duration.Duration._
import scala.concurrent.duration._

object SwitchIfEmptySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount % 2 == 1)
        Observable.empty.switchIfEmpty(Observable.range(0, sourceCount))
      else
        Observable.range(0, sourceCount)

      Sample(o, sourceCount, sourceCount * (sourceCount - 1) / 2, Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[Sample] = {
    val obs1 = Observable.empty.switchIfEmpty(Observable.range(0, 1000).delayOnNext(1.second).map(_ + 1))
    val obs2 = Observable.empty.delayOnComplete(1.second)
      .switchIfEmpty(Observable.range(0, 1000).delayOnNext(1.second).map(_ + 1))

    Seq(
      Sample(obs1, 0, 0, 0.seconds, 0.seconds),
      Sample(obs2, 0, 0, 0.seconds, 0.seconds)
    )
  }
}
