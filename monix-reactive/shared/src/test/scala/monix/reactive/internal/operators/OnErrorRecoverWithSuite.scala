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

import monix.execution.exceptions.DummyException
import monix.reactive.Observable
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero

object OnErrorRecoverWithSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val fallback = Observable.range(0, 10)
    val source = Observable.range(0, sourceCount)
      .endWithError(DummyException("expected"))

    val obs = source.onErrorHandleWith {
      case DummyException("expected") =>
        fallback
      case other =>
        Observable.raiseError(other)
    }

    val sum = sourceCount * (sourceCount-1) / 2 + 9 * 5
    Sample(obs, sourceCount+10, sum, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount <= 1) None else Some {
      val fallback = Observable.range(0, 10)
      val source = Observable.range(0, sourceCount).endWithError(ex)

      val obs = source.onErrorHandleWith {
        case DummyException("not happening") =>
          fallback
        case other =>
          Observable.raiseError(other)
      }

      val sum = sourceCount * (sourceCount-1) / 2
      Sample(obs, sourceCount, sum, Zero, Zero)
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val source = Observable.range(0, sourceCount)
      .endWithError(DummyException("expected"))

    val obs = source.onErrorHandleWith {
      case DummyException("expected") =>
        throw ex
      case other =>
        Observable.raiseError(other)
    }

    val sum = sourceCount * (sourceCount-1) / 2
    Sample(obs, sourceCount, sum, Zero, Zero)
  }

  override def cancelableObservables() = {
    val fallback = Observable.range(0, 10).delayOnNext(1.second)
    val sample = Observable.range(0, 10).map(_ => 1L)
      .delayOnNext(1.second)
      .endWithError(DummyException("expected"))
      .onErrorHandleWith {
        case DummyException("expected") => fallback
        case other => Observable.raiseError(other)
      }

    Seq(
      Sample(sample, 0, 0, 0.seconds, 0.seconds),
      Sample(sample, 10, 10, 10.seconds, 0.seconds)
    )
  }
}
