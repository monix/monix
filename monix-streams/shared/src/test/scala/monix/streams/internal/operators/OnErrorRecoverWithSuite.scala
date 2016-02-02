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

package monix.streams.internal.operators

import monix.streams.exceptions.DummyException
import monix.streams.Observable
import scala.concurrent.duration.Duration.Zero

object OnErrorRecoverWithSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val fallback = Observable.range(0, 10)
    val source = Observable.range(0, sourceCount)
      .endWithError(DummyException("expected"))

    val obs = source.onErrorRecoverWith {
      case DummyException("expected") =>
        fallback
    }

    val sum = sourceCount * (sourceCount-1) / 2 + 9 * 5
    Sample(obs, sourceCount+10, sum, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val fallback = Observable.range(0, 10)
    val source = Observable.range(0, sourceCount).endWithError(ex)

    val obs = source.onErrorRecoverWith {
      case DummyException("not happening") =>
        fallback
    }

    val sum = sourceCount * (sourceCount-1) / 2
    Sample(obs, sourceCount, sum, Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val source = Observable.range(0, sourceCount)
      .endWithError(DummyException("expected"))

    val obs = source.onErrorRecoverWith {
      case DummyException("expected") =>
        throw ex
    }

    val sum = sourceCount * (sourceCount-1) / 2
    Sample(obs, sourceCount, sum, Zero, Zero)
  }
}
