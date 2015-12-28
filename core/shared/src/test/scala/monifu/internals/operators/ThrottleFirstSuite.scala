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

package monifu.internals.operators

import monifu.Observable
import concurrent.duration._

object ThrottleFirstSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    if (sourceCount == 1) {
      val o = Observable.unitDelayed(500.millis, 100L).throttleFirst(1.second)
      Sample(o, 1, 100, 500.millis, 1.second)
    }
    else {
      val div2 = sourceCount / 2 * 2
      val o = Observable.intervalAtFixedRate(500.millis)
        .take(div2)
        .throttleFirst(1.second)

      val count = div2 / 2
      Sample(o, count, count * (count - 1), Duration.Zero, 1.second)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
}
