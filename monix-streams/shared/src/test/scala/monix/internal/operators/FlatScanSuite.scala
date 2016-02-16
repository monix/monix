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

package monix.internal.operators

import monix.Observable

import scala.concurrent.duration.Duration.Zero

object FlatScanSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .flatScan(1L)((acc, elem) => Observable.repeat(acc + elem).take(3))

    val sum = (0 until sourceCount).map(x => (1 to x).sum + 1L).sum * 3
    Sample(o, sourceCount * 3, sum, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount+1).endWithError(ex)
      .flatScan(1L)((acc, elem) => Observable.repeat(acc + elem).take(3))

    val sum = (0 until sourceCount).map(x => (1 to x).sum + 1L).sum * 3
    Sample(o, sourceCount * 3, sum, Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount+1).flatScan(1L) { (acc, elem) =>
      if (elem == sourceCount) throw ex else
        Observable.repeat(acc + elem).take(3)
    }

    val sum = (0 until sourceCount).map(x => (1 to x).sum + 1L).sum * 3
    Sample(o, sourceCount * 3, sum, Zero, Zero)
  }
}
