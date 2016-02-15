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

import monix.execution.Ack
import Ack.Continue
import monix.{Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[monix] object throttle {
  /** Implementation for [[Observable.throttleFirst]] */
  def first[T](self: Observable[T], interval: FiniteDuration): Observable[T] =
    Observable.unsafeCreate { downstream =>
      import downstream.{scheduler => s}

      self.unsafeSubscribeFn(new Observer[T] {
        private[this] val intervalMs = interval.toMillis
        private[this] var nextChange = 0L

        def onNext(elem: T): Future[Ack] = {
          val rightNow = s.currentTimeMillis()
          if (nextChange <= rightNow) {
            nextChange = rightNow + intervalMs
            downstream.onNext(elem)
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          downstream.onError(ex)
        }

        def onComplete(): Unit = {
          downstream.onComplete()
        }
      })
    }
}
