/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

package monifu.reactive.operators

import monifu.concurrent.cancelables.{BooleanCancelable, RefCountCancelable}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive._
import monifu.reactive.internals._
import monifu.reactive.observers.SynchronousObserver
import scala.concurrent.Future

object switch {
  /**
   * Implementation for [[Observable.concat]].
   */
  def apply[T, U](source: Observable[T])(implicit ev: T <:< Observable[U]): Observable[U] =
    Observable.create { subscriber:Subscriber[U] =>
      implicit val s = subscriber.scheduler
      val observerU = subscriber.observer

      source.unsafeSubscribe(new SynchronousObserver[T] { self =>
        // Global subscription, is canceled by the downstream
        // observer and if canceled all streaming is supposed to stop
        private[this] val upstream = BooleanCancelable()

        // MUST BE synchronized by `self`
        private[this] var ack: Future[Ack] = Continue
        // To be accessed only in `self.onNext`
        private[this] var current: BooleanCancelable = null

        private[this] val refCount = RefCountCancelable {
          self.synchronized {
            ack.onContinueSignalComplete(observerU)
          }
        }

        def onNext(childObservable: T) = {
          if (upstream.isCanceled) Cancel else {
            // canceling current observable in order to
            // start the new stream
            val activeSubscription = refCount.acquire()
            if (current != null) current.cancel()
            current = activeSubscription

            childObservable.unsafeSubscribe(new Observer[U] {
              def onNext(elem: U) = self.synchronized {
                if (upstream.isCanceled || activeSubscription.isCanceled)
                  Cancel
                else {
                  ack = ack.onContinueStreamOnNext(observerU, elem)
                  ack.ifCanceledDoCancel(upstream)
                  ack
                }
              }

              def onError(ex: Throwable): Unit =
                self.onError(ex)

              def onComplete(): Unit = {
                // NOTE: we aren't sending this onComplete signal downstream to our observerU
                // we will eventually do that after all of them are complete
                activeSubscription.cancel()
              }
            })

            Continue
          }
        }

        def onError(ex: Throwable): Unit =
          self.synchronized {
            if (!upstream.isCanceled) {
              upstream.cancel()
              ack.onContinueSignalError(observerU, ex)
            }
          }

        def onComplete(): Unit = {
          refCount.cancel()
        }
      })
    }
}
