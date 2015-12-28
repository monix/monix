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

package monifu.internals.operators

import monifu.Notification.{OnComplete, OnError, OnNext}
import monifu.{Notification, Observable, Ack, Observer}
import monifu.internals._
import scala.concurrent.Future


private[monifu] object materialize {
  /**
   * Implementation for [[Observable.materialize]].
   */
  def apply[T](source: Observable[T]): Observable[Notification[T]] =
    Observable.create[Notification[T]] { subscriber =>
      import subscriber.{scheduler => s}

      source.onSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          subscriber.onNext(OnNext(elem))
        }

        def onError(ex: Throwable): Unit = {
          subscriber.onNext(OnError(ex))
            .onContinueSignalError(subscriber, ex)
        }

        def onComplete(): Unit = {
          subscriber.onNext(OnComplete)
            .onContinueSignalComplete(subscriber)
        }
      })
    }
}
