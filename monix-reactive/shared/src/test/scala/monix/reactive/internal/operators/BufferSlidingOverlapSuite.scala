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

object BufferSlidingOverlapSuite extends BaseOperatorSuite {
  val waitNext = Duration.Zero
  val waitFirst = Duration.Zero

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "count must be strictly positive")
    if (sourceCount > 1) Some {
      val divBy4 = sourceCount / 4 * 4
      val o = Observable.range(0, divBy4)
        .map(_ % 4)
        .bufferSliding(8,4)
        .flatMap(x => Observable.fromIterable(x))

      val count = 8 + (divBy4 - 8) * 2
      val sum = (count / 4) * 6
      Sample(o, count, sum, waitFirst, waitNext)
    }
    else Some {
      val o = Observable.now(1L)
        .bufferSliding(2,1).flatMap(x => Observable.fromIterable(x))
      Sample(o, 1, 1, waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val o = Observable.range(0,1000).delayOnNext(1.second)
      .bufferSliding(2,1).flatMap(x => Observable.fromIterable(x))
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }
}
