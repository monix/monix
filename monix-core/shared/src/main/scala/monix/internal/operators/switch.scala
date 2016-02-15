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
import monix.execution.cancelables.{BooleanCancelable, SerialCancelable}
import monix.{Subscriber, Observer, Observable}
import Ack.{Cancel, Continue}
import monix.internal._
import scala.concurrent.Future

private[monix] object switch {
  /**
    * Implementation for [[Observable.switch]].
    */
  def apply[T,U](source: Observable[T])(implicit ev: T <:< Observable[U]): Observable[U] = {
    Observable.unsafeCreate { observerU: Subscriber[U] =>
      import observerU.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] { self =>
        // Global subscription, is canceled by the downstream
        // observer and if canceled all streaming is supposed to stop
        private[this] val upstream = SerialCancelable()
        // MUST BE synchronized by `self`
        private[this] var ack: Future[Ack] = Continue
        // MUST BE synchronized by `self`

        def onNext(childObservable: T) = self.synchronized {
          if (upstream.isCanceled) Cancel else {
            // canceling current observable in order to
            // start the new stream
            val activeRef = BooleanCancelable()
            upstream := activeRef

            ack.fastFlatMap {
              case Continue =>
                childObservable.unsafeSubscribeFn(new Observer[U] {
                  def onNext(elem: U) = self.synchronized {
                    if (activeRef.isCanceled) Cancel else {
                      ack = ack.onContinueStreamOnNext(observerU, elem)
                      ack.ifCanceledDoCancel(upstream)
                    }
                  }

                  def onComplete(): Unit = ()
                  def onError(ex: Throwable): Unit = {
                    self.onError(ex)
                  }
                })

                Continue

              case Cancel =>
                Cancel
            }
          }
        }

        def onError(ex: Throwable): Unit = self.synchronized {
          if (!upstream.isCanceled) {
            upstream.cancel()
            ack.onContinueSignalError(observerU, ex)
          }
        }

        def onComplete(): Unit = self.synchronized {
          if (!upstream.isCanceled) {
            upstream.cancel()
            ack.onContinueSignalComplete(observerU)
          }
        }
      })
    }
  }
}
