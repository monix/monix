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

import scala.concurrent.duration.Duration

object WhileBusyBufferEventsSuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    val source = Observable.range(0, sourceCount)
      .whileBusyBufferEvents(sourceCount / 3 + 1)
      .flatMap(Observable.fromIterable)

    val sum = sourceCount.toLong * (sourceCount - 1) / 2
    Sample(source, sourceCount, sum, Duration.Zero, Duration.Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val source = Observable.range(0, sourceCount)
      .endWithError(ex)
      .whileBusyBufferEvents(sourceCount / 3 + 1)
      .flatMap(Observable.fromIterable)

    val sum = sourceCount.toLong * (sourceCount - 1) / 2
    Sample(source, sourceCount, sum, Duration.Zero, Duration.Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None
}
