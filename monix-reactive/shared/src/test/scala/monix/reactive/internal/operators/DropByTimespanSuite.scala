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

object DropByTimespanSuite extends BaseOperatorSuite {
  val waitFirst = 2500.millis
  val waitNext = 500.millis

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  def sum(sourceCount: Int) =
    (0 until sourceCount).map(_ + 5).sum
  def count(sourceCount: Int) =
    sourceCount

  def createObservable(sourceCount: Int) = Some {
    require(sourceCount > 0, "sourceCount should be strictly positive")

    val o = Observable.intervalAtFixedRate(500.millis)
      .take(sourceCount + 5)
      .dropByTimespan(2300.millis)

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val source = Observable.intervalAtFixedRate(500.millis)
        .take(sourceCount + 5)
      val o = createObservableEndingInError(source, ex)
        .dropByTimespan(2300.millis)

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  override def cancelableObservables(): Seq[Sample] = {
    val o = Observable.intervalAtFixedRate(500.millis)
      .dropByTimespan(2300.millis)
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }
}