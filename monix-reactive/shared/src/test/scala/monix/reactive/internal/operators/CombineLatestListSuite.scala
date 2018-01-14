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
import monix.reactive.Observable.now
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero

object CombineLatestListSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val source = Observable.range(0, sourceCount)
    val o: Observable[Long] =
      Observable.combineLatestList(now(1L), now(2L), now(3L), now(4L), now(5L), source)
        .map { seq => seq.sum }

    val sum = (0 until sourceCount).map(_ + 15).sum
    Sample(o, sourceCount, sum, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 = {
      val o1 = Observable.range(0, 10).delayOnNext(1.second)
      val o2 = Observable.range(0, 10).delayOnNext(1.second)
      val o3 = Observable.range(0, 10).delayOnNext(1.second)
      val o4 = Observable.range(0, 10).delayOnNext(1.second)
      val o5 = Observable.range(0, 10).delayOnNext(1.second)
      val o6 = Observable.range(0, 10).delayOnNext(1.second)
      Observable.combineLatestList(o1,o2,o3,o4,o5,o6).map { seq => seq.sum }
    }

    Seq(Sample(sample1, 0, 0, 0.seconds, 0.seconds))
  }
}