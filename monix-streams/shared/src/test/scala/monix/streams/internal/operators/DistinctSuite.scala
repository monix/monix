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
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero

object DistinctSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val seq = (0 until sourceCount)
      .flatMap(i => Seq(i, i, i))
      .map(_.toLong)

    val o = Observable.fromIterable(seq).distinct
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    if (sourceCount == 1) {
      val o = Observable.now(1L).endWithError(ex).distinct
      Sample(o, 1, 1, Zero, Zero)
    } else {
      val seq = (0 until sourceCount)
        .flatMap(i => Seq(i, i, i))
        .map(_.toLong)

      val source = Observable.fromIterable(seq)
      val o = createObservableEndingInError(source, ex).distinct
      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount - 1) / 2
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val o = Observable.now(1L).delayOnNext(1.second).distinct
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }
}
