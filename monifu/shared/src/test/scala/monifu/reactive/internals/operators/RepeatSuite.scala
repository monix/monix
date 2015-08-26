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
import concurrent.duration.Duration.Zero

object RepeatSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int) = {
    (0 until sourceCount).foldLeft(0L)((acc, e) => acc + e % 5)
  }

  def observable(sourceCount: Int) = Some {
    val o = Observable.range(0, 5).repeat.take(sourceCount)
    Sample(o, sourceCount, sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0, 5), ex).repeat
    Sample(o, 5, sum(5), Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
}
