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
import monix.reactive.OverflowStrategy.Unbounded
import scala.concurrent.duration.Duration.Zero
import scala.concurrent.duration._

object AsyncBoundarySuite extends BaseOperatorSuite {
  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int): Long = sourceCount.toLong * (sourceCount + 1) / 2

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.now(1L)
          .asyncBoundary(Unbounded)
      else
        Observable.range(1, sourceCount+1, 1)
          .asyncBoundary(Unbounded)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[Sample] = {
    val obs = Observable.range(0, 1000).delayOnNext(1.second).asyncBoundary(Unbounded)
    Seq(Sample(obs, 0, 0, 0.seconds, 0.seconds))
  }
}
