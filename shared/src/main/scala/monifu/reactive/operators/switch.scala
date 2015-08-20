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

package monifu.reactive.operators

import monifu.concurrent.cancelables.{RefCountCancelable, SerialCancelable}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive._
import monifu.reactive.internals._
import monifu.reactive.observers.SynchronousObserver
import scala.collection.mutable
import scala.concurrent.Future

object switch {
  /**
   * Implementation for [[Observable.concat]].
   */
  def apply[T, U](source: Observable[T], delayErrors: Boolean)
      (implicit ev: T <:< Observable[U]): Observable[U] = {

    Observable.create { subscriber:Subscriber[U] =>
      implicit val s = subscriber.scheduler
      val observerU = subscriber.observer

      source.unsafeSubscribe(new SynchronousObserver[T] { self =>
        // Global subscription, is canceled by the downstream
        // observer and if canceled all streaming is supposed to stop
        private[this] val upstream = SerialCancelable()

        // MUST BE synchronized by `self`
        private[this] var ack: Future[Ack] = Continue
        // MUST BE synchronized by `self`
        private[this] val errors = if (delayErrors)
          mutable.ArrayBuffer.empty[Throwable] else null

        private[this] val refCount = RefCountCancelable {
          self.synchronized {
            if (delayErrors && errors.nonEmpty) {
              ack.onContinueSignalError(observerU, CompositeException(errors))
              errors.clear()
            }
            else {
              ack.onContinueSignalComplete(observerU)
            }
          }
        }

        def onNext(childObservable: T) = {
          if (upstream.isCanceled) Cancel else {
            // canceling current observable in order to
            // start the new stream
            val refID = refCount.acquire()
            upstream := refID

            childObservable.unsafeSubscribe(new Observer[U] {
              def onNext(elem: U) = self.synchronized {
                if (refID.isCanceled) Cancel else {
                  ack = ack.onContinueStreamOnNext(observerU, elem)
                  ack.ifCanceledDoCancel(upstream)
                }
              }

              def onError(ex: Throwable): Unit = {
                if (delayErrors) self.synchronized {
                  errors += ex
                  onComplete()
                }
                else {
                  self.onError(ex)
                }
              }

              def onComplete(): Unit = {
                // NOTE: we aren't sending this onComplete signal downstream to
                // our observerU we will eventually do that after all of them
                // are complete
                refID.cancel()
              }
            })

            Continue
          }
        }

        def onError(ex: Throwable): Unit =
          self.synchronized {
            if (delayErrors) {
              errors += ex
              onComplete()
            }
            else if (!upstream.isCanceled) {
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
}
