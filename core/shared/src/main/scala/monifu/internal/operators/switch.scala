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

package monifu.internal.operators

import monifu.concurrent.cancelables.{BooleanCancelable, SerialCancelable}
import monifu.Ack.{Cancel, Continue}
import monifu._
import monifu.internal._
import scala.concurrent.Future


private[monifu] object switch {
  /**
   * Implementation for [[Observable.switch]].
   */
  def apply[T,U](source: Observable[T])(implicit ev: T <:< Observable[U]): Observable[U] = {
    Observable.create { observerU: Subscriber[U] =>
      import observerU.{scheduler => s}

      source.onSubscribe(new Observer[T] { self =>
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
                childObservable.onSubscribe(new Observer[U] {
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
