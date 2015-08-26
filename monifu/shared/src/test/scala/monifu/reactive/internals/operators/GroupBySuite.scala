/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.internals.operators

import monifu.reactive.Observable
import scala.concurrent.duration.Duration.Zero

object GroupBySuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .groupBy(_ % 5)
      .mergeMap(o => o.map(x => o.key + x))

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    sourceCount

  def sum(sourceCount: Int) = {
    (0 until sourceCount).map(x => x + x % 5).sum
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .groupBy(_ % 5)
      .flatMap(o => o.map(x => o.key + x))

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(1, sourceCount)
      .groupBy(x => if (x == 2) throw ex else x)
      .concat

    Sample(o, 1, 1, Zero, Zero)
  }
}

