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
import scala.concurrent.duration._

object WithLatestFrom2Suite extends BaseOperatorSuite {
  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int): Long =
    sourceCount.toLong * (sourceCount + 1) / 2 + sourceCount * 20

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val other = Observable.fromIterable(0 to 10)
      val o = if (sourceCount == 1)
        Observable.now(1L).delaySubscription(1.second)
          .withLatestFrom2(other, other)(_ + _ + _)
      else
        Observable.range(1, sourceCount+1, 1)
          .delaySubscription(1.second)
          .withLatestFrom2(other, other)(_ + _ + _)

      Sample(o, count(sourceCount), sum(sourceCount), 1.second, Zero)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val other = Observable.fromIterable(0 to 10)
    val o = if (sourceCount == 1)
      Observable.now(1L).delaySubscription(1.second).endWithError(ex)
        .withLatestFrom2(other, other)(_ + _ + _)
    else
      Observable.range(1, sourceCount+1, 1)
        .delaySubscription(1.second)
        .endWithError(ex)
        .withLatestFrom2(other, other)(_ + _ + _)

    Sample(o, count(sourceCount), sum(sourceCount), 1.second, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val other = Observable.fromIterable(0 to 10)
      val o = if (sourceCount == 1)
        Observable.now(1L).delaySubscription(1.second)
          .withLatestFrom2(other, other)((x1,x2,x3) => throw ex)
      else
        Observable.range(1, sourceCount+1, 1)
          .delaySubscription(1.second)
          .withLatestFrom2(other,other) { (x1,x2,x3) =>
            if (x1 == sourceCount)
              throw ex
            else
              x1 + x2 + x3
          }

      Sample(o, count(sourceCount-1), sum(sourceCount-1), 1.second, Zero)
    }
  }

  override def cancelableObservables(): Seq[Sample] = {
    val other = Observable.now(1).delaySubscription(1.second)
    val sample = Observable.now(1L).delaySubscription(2.seconds)
      .withLatestFrom2(other,other)(_+_+_)

    Seq(
      Sample(sample, 0, 0, 0.seconds, 0.seconds),
      Sample(sample, 0, 0, 1.seconds, 0.seconds)
    )
  }
}