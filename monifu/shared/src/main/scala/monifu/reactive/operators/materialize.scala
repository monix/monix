/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import monifu.reactive.Notification.{OnComplete, OnError, OnNext}
import monifu.reactive.{Notification, Observable, Ack, Observer}
import monifu.reactive.internals._
import scala.concurrent.Future


object materialize {
  /**
   * Implementation for [[Observable.materialize]].
   */
  def apply[T](source: Observable[T]): Observable[Notification[T]] =
    Observable.create[Notification[T]] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          observer.onNext(OnNext(elem))
        }

        def onError(ex: Throwable): Unit = {
          observer.onNext(OnError(ex))
            .onContinueSignalError(observer, ex)
        }

        def onComplete(): Unit = {
          observer.onNext(OnComplete)
            .onContinueSignalComplete(observer)
        }
      })
    }
}
