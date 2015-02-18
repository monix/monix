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

import monifu.concurrent.cancelables.{BooleanCancelable, RefCountCancelable}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.observers.{BufferedSubscriber, SynchronousObserver}
import monifu.reactive.{Ack, BufferPolicy, Observable, Observer}
import scala.concurrent.Promise

object flatten {
  /**
   * Implementation for [[Observable.concat]].
   */
  def concat[T,U](source: Observable[T])(implicit ev: T <:< Observable[U]): Observable[U] = {
    Observable.create[U] { subscriber =>
      implicit val s = subscriber.scheduler
      val observerU = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] val refCount = RefCountCancelable(observerU.onComplete())

        def onNext(childObservable: T) = {
          val upstreamPromise = Promise[Ack]()
          val refID = refCount.acquire()

          childObservable.unsafeSubscribe(new Observer[U] {
            def onNext(elem: U) = {
              observerU.onNext(elem)
                .ifCancelTryCanceling(upstreamPromise)
            }

            def onError(ex: Throwable): Unit = {
              // error happened, so signaling both the main thread that it should stop
              // and the downstream consumer of the error
              upstreamPromise.trySuccess(Cancel)
              observerU.onError(ex)
            }

            def onComplete(): Unit = {
              // NOTE: we aren't sending this onComplete signal downstream to our observerU
              // instead we are just instructing upstream to send the next observable
              upstreamPromise.trySuccess(Continue)
              refID.cancel()
            }
          })

          upstreamPromise.future
        }

        def onError(ex: Throwable) = {
          // oops, error happened on main thread, piping that along should cancel everything
          observerU.onError(ex)
        }

        def onComplete() = {
          refCount.cancel()
        }
      })
    }
  }

  def merge[T,U](source: Observable[T], bufferPolicy: BufferPolicy)
      (implicit ev: T <:< Observable[U]): Observable[U] = {

    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val buffer = BufferedSubscriber(subscriber.observer, bufferPolicy)
      val observerU = buffer.observer

      source.unsafeSubscribe(new SynchronousObserver[T] {
        private[this] val refCount =
          RefCountCancelable(observerU.onComplete())
        private[this] val streamActivity =
          BooleanCancelable()

        def onNext(childObservable: T) = {
          val refID = refCount.acquire()

          childObservable.unsafeSubscribe(new Observer[U] {
            def onNext(elem: U) = {
              if (streamActivity.isCanceled)
                Cancel
              else
                observerU.onNext(elem)
                  .ifCanceledDoCancel(streamActivity)
            }

            def onError(ex: Throwable): Unit = {
              streamActivity.cancel()
              observerU.onError(ex)
            }

            def onComplete(): Unit = {
              // NOTE: we aren't sending this onComplete signal downstream to our observerU
              // we will eventually do that after all of them are complete
              refID.cancel()
            }
          })

          if (streamActivity.isCanceled)
            Cancel
          else
            Continue
        }

        def onError(ex: Throwable) = {
          // oops, error happened on main thread, piping that along should cancel everything
          observerU.onError(ex)
        }

        def onComplete() = {
          refCount.cancel()
        }
      })
    }
  }
}
