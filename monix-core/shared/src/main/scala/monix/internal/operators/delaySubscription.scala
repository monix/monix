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
import Ack.Cancel
import monix.internal.concurrent.PromiseSuccessRunnable
import monix.{Observable, Observer}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

private[monix] object delaySubscription {
  /**
    * Implementation for [[Observable.delaySubscription]].
    */
  def onTrigger[T,U](source: Observable[T], trigger: Observable[U]): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      trigger.unsafeSubscribeFn(new Observer[U] {
        def onNext(elem: U): Future[Ack] = {
          source.unsafeSubscribeFn(subscriber)
          Cancel
        }

        def onError(ex: Throwable): Unit = {
          subscriber.onError(ex)
        }

        def onComplete(): Unit = {
          source.unsafeSubscribeFn(subscriber)
        }
      })
    }

  /**
    * Implementation for [[Observable.delaySubscription]].
    */
  def onTimespan[T](source: Observable[T], timespan: FiniteDuration): Observable[T] =
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}
      val underlying = source.delaySubscription {
        val p = Promise[Unit]()
        s.scheduleOnce(timespan.length, timespan.unit, PromiseSuccessRunnable(p, ()))
        p.future
      }

      underlying.unsafeSubscribeFn(subscriber)
    }
}
