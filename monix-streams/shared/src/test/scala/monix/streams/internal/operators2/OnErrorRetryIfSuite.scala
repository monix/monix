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

package monix.streams.internal.operators2

import monix.streams.Observable
import monix.streams.exceptions.DummyException
import org.sincron.atomic.Atomic
import scala.concurrent.duration.{Duration, _}

object OnErrorRetryIfSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val retriesCount = Atomic(0)
    val ex = DummyException("expected")

    val o = Observable.range(0, sourceCount).endWithError(ex)
      .onErrorRetryIf { case DummyException("expected") => retriesCount.incrementAndGet() <= 3 }
      .onErrorRecover { case _ => 10L }

    val count = sourceCount * 4 + 1
    val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4 + 10
    Sample(o, count, sum, Duration.Zero, Duration.Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) {
      val o = Observable.now(1L).endWithError(ex).onErrorRetryIf(_ => false)
      Some(Sample(o,1,1,Duration.Zero,Duration.Zero))
    } else {
      val retriesCount = Atomic(0)

      val o = Observable.range(0, sourceCount).endWithError(ex).onErrorRetryIf {
        case _ => retriesCount.incrementAndGet() <= 3
      }

      val count = sourceCount * 4
      val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4
      Some(Sample(o, count, sum, Duration.Zero, Duration.Zero))
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val retriesCount = Atomic(0)
    val o = Observable.range(0, sourceCount).endWithError(DummyException("unexpected"))
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

  override def cancelableObservables() = {
    val dummy = DummyException("dummy")
    val sample = Observable.range(0, 20).map(_ => 1L)
      .endWithError(dummy).delaySubscription(1.second)
      .onErrorRetryIf(ex => true)

    Seq(
      Sample(sample, 0, 0, 0.seconds, 0.seconds),
      Sample(sample, 20, 20, 1.seconds, 0.seconds),
      Sample(sample, 40, 40, 2.seconds, 0.seconds)
    )
  }
}
