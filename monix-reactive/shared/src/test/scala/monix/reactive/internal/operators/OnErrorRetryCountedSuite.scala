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
import monix.execution.exceptions.DummyException
import scala.concurrent.duration.{Duration, _}

object OnErrorRetryCountedSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val ex = DummyException("expected")
    val o = Observable.range(0, sourceCount).endWithError(ex)
      .onErrorRestart(3)
      .onErrorHandle { case _ => 10L }

    val count = sourceCount * 4 + 1
    val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4 + 10
    Sample(o, count, sum, Duration.Zero, Duration.Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount <= 1) None else Some {
      val o = Observable.range(0, sourceCount).endWithError(ex).onErrorRestart(3)

      val count = sourceCount * 4
      val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4
      Sample(o, count, sum, Duration.Zero, Duration.Zero)
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val dummy = DummyException("dummy")
    val sample = Observable.range(0, 20).map(_ => 1L)
      .endWithError(dummy).delaySubscription(1.second)
      .onErrorRestart(100)

    Seq(
      Sample(sample, 0, 0, 0.seconds, 0.seconds),
      Sample(sample, 20, 20, 1.seconds, 0.seconds),
      Sample(sample, 40, 40, 2.seconds, 0.seconds)
    )
  }
}
