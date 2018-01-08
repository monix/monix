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

object DebounceSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.interval(2.seconds)
      .debounce(1.second)
      .take(sourceCount)

    val count = sourceCount
    val sum = sourceCount * (sourceCount - 1) / 2
    Sample(o, count, sum, 1.second, 2.second)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable
      .interval(2.seconds).take(sourceCount), ex)
      .debounce(1.second)

    val count = sourceCount - 1
    val sum = sourceCount * (sourceCount - 1) / 2
    Sample(o, count, sum, 1.second, 2.second)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 = Observable.interval(2.seconds).map(_ => 1L).debounce(1.second)

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 1, 1, 1.seconds, 0.seconds),
      Sample(sample1, 1, 1, 2.seconds, 0.seconds),
      Sample(sample1, 2, 2, 3.seconds, 0.seconds)
    )
  }
}
