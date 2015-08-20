/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

package monifu.reactive.operators

import monifu.reactive.Observable
import scala.concurrent.duration._

object SwitchSuite extends BaseOperatorSuite {
  def createChild() = {
    Observable.interval(1.second).take(2) ++
      Observable.interval(1.second).drop(3)
  }

  def observable(sourceCount: Int) = Some {
    val o = Observable.interval(2.seconds)
      .take(sourceCount)
      .map(i => (if (i < sourceCount-1) createChild() else Observable.interval(1.second)).take(sourceCount))
      .switch

    val count = (sourceCount - 1) * 2 + sourceCount
    val sum = (sourceCount - 1) + (1 until sourceCount).sum
    Sample(o, count, sum, waitFirst, waitNext)
  }

  def waitFirst = 0.seconds
  def waitNext = 1.second

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    def createChild(i: Long) = {
      if (i < sourceCount-1)
        Observable.interval(1.second).take(2)
      else
        Observable.interval(1.second)
          .take(sourceCount)
    }

    val source = Observable.interval(2.seconds).take(sourceCount)
    val endingInError = createObservableEndingInError(source, ex)
    val o = endingInError.map(createChild).switch

    val count = (sourceCount - 1) * 2
    val sum = sourceCount - 1

    Sample(o, count, sum, waitFirst, waitNext)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None
}
