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
import scala.concurrent.duration.Duration.Zero

object ZipWithIndexSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = Observable.range(1, sourceCount + 1)
        .zipWithIndex.map { case (elem, index) => elem + index }

      val c = sourceCount
      val sum = c * (c + 1) / 2 + c * (c - 1) / 2
      Sample(o, sourceCount, sum, Zero, Zero)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = createObservableEndingInError(Observable.range(1, sourceCount + 1), ex)
        .zipWithIndex.map { case (elem, index) => elem + index }

      val c = sourceCount
      val sum = c * (c + 1) / 2 + c * (c - 1) / 2
      Sample(o, sourceCount, sum, Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[Sample] = {
    val sample = Observable.range(0, 10).delayOnNext(1.second)
      .zipWithIndex.map { case (elem, index) => elem + index }
    Seq(Sample(sample, 0, 0, 0.seconds, 0.seconds))
  }
}
