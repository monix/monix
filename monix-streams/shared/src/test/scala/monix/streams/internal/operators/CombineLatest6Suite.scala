/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams.internal.operators

import monix.streams.Observable
import Observable.now
import monix.streams.Observable
import scala.concurrent.duration.Duration.Zero

object CombineLatest6Suite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val source = Observable.range(0, sourceCount)

    val o = Observable.combineLatest(now(1), now(2), now(3), now(4), now(5), source)
      .map { case (t1, t2, t3, t4, t5, t6) => t1 + t2 + t3 + t4 + t5 + t6 }

    val sum = (0 until sourceCount).map(_ + 15).sum
    Sample(o, sourceCount, sum, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
}