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
import monifu.reactive.exceptions.DummyException
import scala.concurrent.duration.Duration

object OnErrorRetryUnlimitedSuite extends BaseOperatorSuite {
  def create(sourceCount: Int, maxSubscriptions: Int, ex: Throwable) = {
    var subscriptions = 0

    Observable.create[Long] { subscriber =>
      if (subscriptions < maxSubscriptions) {
        subscriptions += 1
        Observable.range(0, sourceCount)
          .endWithError(ex)
          .onSubscribe(subscriber)
      }
      else {
        Observable.range(0, sourceCount)
          .onSubscribe(subscriber)
      }
    }
  }

  def createObservable(sourceCount: Int) = Some {
    val o = create(sourceCount, 3, DummyException("expected"))
      .onErrorRetryUnlimited

    val count = sourceCount * 4
    val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4
    Sample(o, count, sum, Duration.Zero, Duration.Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
}
