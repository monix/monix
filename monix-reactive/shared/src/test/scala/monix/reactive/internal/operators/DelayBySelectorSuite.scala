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

object DelayBySelectorSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val source = Observable.range(0, sourceCount)
    val o = source.delayOnNextBySelector(x => Observable.now(x).delaySubscription(1.second))
    val c = sourceCount
    Sample(o, c, c * (c-1) / 2, 1.second, 1.second)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val source = createObservableEndingInError(Observable.range(0, sourceCount), ex)
    val o = source.delayOnNextBySelector(x => Observable.now(x).delaySubscription(1.second))
    val c = sourceCount
    Sample(o, c-1, (c-1) * (c-2) / 2, 1.second, 1.second)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val source = Observable.range(0, sourceCount+1)
    val o = source.delayOnNextBySelector { x =>
      if (x < sourceCount)
        Observable.now(x).delaySubscription(1.second)
      else
        throw ex
    }

    val c = sourceCount
    Sample(o, c, c * (c-1) / 2, 1.second, 1.second)
  }

  override def cancelableObservables() = {
    val o = Observable.now(1L)
      .delayOnNextBySelector(x => Observable.now(x).delaySubscription(1.second))
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }
}