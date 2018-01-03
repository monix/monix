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

object RepeatSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int) = {
    (0 until sourceCount).foldLeft(0L)((acc, e) => acc + e % 5)
  }

  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, 5).repeat.take(sourceCount)
    Sample(o, sourceCount, sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount <= 1) None else Some {
      val o = createObservableEndingInError(Observable.range(0, 5), ex).repeat
      Sample(o, 5, sum(5), Zero, Zero)
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val o = Observable.range(0,5).map(_ => 1L).delayOnNext(1.second).repeat
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }
}
