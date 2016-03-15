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

object MiscNonEmptySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val shouldBeEmpty = (sourceCount % 2) == 0
    val sum = if (shouldBeEmpty) 2L else 1L

    val source = if (shouldBeEmpty)
      Observable.empty
    else
      Observable.range(0, sourceCount)

    val o = source.nonEmptyF.map(x => if (x) 1L else 2L)
    Sample(o, 1, sum, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.error(ex)
      .nonEmptyF.map(x => if (x) 1L else 2L)

    Sample(o, 0, 0, Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  override def cancelableObservables() = {
    val source1 = Observable.empty
      .delayOnComplete(1.second)
      .nonEmptyF.map(x => if (x) 2L else 1L)

    val source2 = Observable.now(1)
      .delayOnNext(1.second)
      .nonEmptyF.map(x => if (x) 2L else 1L)

    Seq(
      Sample(source1, 0, 0, Zero, Zero),
      Sample(source2, 0, 0, Zero, Zero))
  }
}
