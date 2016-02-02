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

import monix.streams.Ack.Cancel
import monix.streams._
import monix.streams.Notification._
import monix.streams.internal.FutureAckExtensions
import scala.concurrent.Future

private[streams] object notification {
  /**
    * Implementation for [[Observable.materialize]].
    */
  def materialize[T](source: Observable[T]): Observable[Notification[T]] =
    Observable.unsafeCreate[Notification[T]] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          subscriber.onNext(OnNext(elem))
        }

        def onError(ex: Throwable): Unit = {
          subscriber.onNext(OnError(ex))
            .onContinueSignalComplete(subscriber)
        }

        def onComplete(): Unit = {
          subscriber.onNext(OnComplete)
            .onContinueSignalComplete(subscriber)
        }
      })
    }

  /**
    * Implementation for [[Observable.dematerialize]].
    */
  def dematerialize[T](source: Observable[Notification[T]]): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[Notification[T]] {
        private[this] var isDone = false

        def onNext(elem: Notification[T]): Future[Ack] = {
          if (isDone) Cancel else
            elem match {
              case OnNext(e) =>
                subscriber.onNext(e)
              case OnError(ex) =>
                isDone = true
                subscriber.onError(ex)
                Cancel
              case OnComplete =>
                isDone = true
                subscriber.onComplete()
                Cancel
            }
        }

        def onError(ex: Throwable): Unit = {
          if (!isDone) {
            isDone = true
            subscriber.onError(ex)
          } else {
            s.reportFailure(ex)
          }
        }

        def onComplete(): Unit = {
          if (!isDone) {
            isDone = true
            subscriber.onComplete()
          }
        }
      })
    }
}
