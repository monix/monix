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

import scalax.concurrent.atomic.Atomic
import monix.Observable
import monix.exceptions.DummyException

import scala.concurrent.duration.Duration

object OnErrorRetryIfSuite extends BaseOperatorSuite {
  def create(sourceCount: Int, maxSubscriptions: Int, ex: Throwable) = {
    var subscriptions = 0

    Observable.unsafeCreate[Long] { subscriber =>
      if (subscriptions < maxSubscriptions) {
        subscriptions += 1
        Observable.range(0, sourceCount)
          .endWithError(ex)
          .unsafeSubscribeFn(subscriber)
      }
      else {
        Observable.range(0, sourceCount)
          .unsafeSubscribeFn(subscriber)
      }
    }
  }

  def createObservable(sourceCount: Int) = Some {
    val retriesCount = Atomic(0)
    val o = create(sourceCount, 3, DummyException("expected")).onErrorRetryIf {
      case DummyException("expected") =>
        retriesCount.incrementAndGet() <= 3
    }

    val count = sourceCount * 4
    val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4
    Sample(o, count, sum, Duration.Zero, Duration.Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val retriesCount = Atomic(0)
    val o = create(sourceCount, 4, ex)
      .onErrorRetryIf(ex => retriesCount.incrementAndGet() <= 3)

    val count = sourceCount * 4
    val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4
    Sample(o, count, sum, Duration.Zero, Duration.Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val retriesCount = Atomic(0)
    val o = create(sourceCount, 4, DummyException("unexpected"))
      .onErrorRetryIf { _ =>
        if (retriesCount.incrementAndGet() <= 3)
          true
        else
          throw ex
      }

    val count = sourceCount * 4
    val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4
    Sample(o, count, sum, Duration.Zero, Duration.Zero)
  }
}
