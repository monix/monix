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

object ReduceSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    if (sourceCount > 1) {
      val o = Observable.range(1, sourceCount+1).reduce(_ + _)
      Sample(o, 1, sum(sourceCount), Zero, Zero)
    }
    else {
      val o = Observable.range(1, 3).reduce(_ + _)
      Sample(o, 1, 3, Zero, Zero)
    }
  }

  def sum(sourceCount: Int): Int =
    sourceCount * (sourceCount + 1) / 2

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(1, sourceCount+1).endWithError(ex).reduce(_ + _)
    Sample(o, 0, 0, Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount+1).reduce { (acc, elem) =>
      if (elem == sourceCount)
        throw ex
      else
        acc + elem
    }

    Sample(o, 0, 0, Zero, Zero)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val o = Observable.range(0, 100).delayOnNext(1.second).reduce(_ + _)
    Seq(Sample(o,0,0,0.seconds,0.seconds))
  }
}
