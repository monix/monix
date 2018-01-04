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

import monix.execution.Cancelable
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import monix.reactive.observers.Subscriber
import scala.concurrent.duration._

object OnErrorRetryUnlimitedSuite extends BaseOperatorSuite {
  def create(sourceCount: Int, maxSubscriptions: Int, ex: Throwable) = {
    var subscriptions = 0
    new Observable[Long] {
      def unsafeSubscribeFn(subscriber: Subscriber[Long]): Cancelable =
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
    val o = create(sourceCount, 3, DummyException("expected"))
      .onErrorRestartUnlimited

    val count = sourceCount * 4
    val sum = 1L * sourceCount * (sourceCount-1) / 2 * 4
    Sample(o, count, sum, Duration.Zero, Duration.Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val dummy = DummyException("dummy")
    val sample = Observable.range(0, 20).map(_ => 1L)
      .endWithError(dummy).delaySubscription(1.second)
      .onErrorRestartUnlimited

    Seq(
      Sample(sample, 0, 0, 0.seconds, 0.seconds),
      Sample(sample, 20, 20, 1.seconds, 0.seconds),
      Sample(sample, 40, 40, 2.seconds, 0.seconds)
    )
  }
}
