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
 *
 */

package monix.streams.internal.operators

import monix.execution.cancelables.RefCountCancelable
import monix.streams.Ack.{Cancel, Continue}
import monix.streams.exceptions.CompositeException
import monix.streams.internal._
import monix.streams.{Ack, Observable, Observer}
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

private[monix] object flatScan {
  /**
    * Implementation for [[Observable.flatScan]].
    */
  def apply[T,R](source: Observable[T], initial: R)(op: (R, T) => Observable[R]) =
    Observable.unsafeCreate[R] { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] val refCount = RefCountCancelable(o.onComplete())
        private[this] var state = initial

        def onNext(elem: T) = {
          // for protecting user calls
          var streamError = true
          try {
            val upstreamPromise = Promise[Ack]()
            val newState = op(state, elem)
            streamError = false

            val refID = refCount.acquire()

            newState.unsafeSubscribeFn(new Observer[R] {
              def onNext(elem: R): Future[Ack] = {
                state = elem
                o.onNext(elem)
                  .ifCancelTryCanceling(upstreamPromise)
              }

              def onError(ex: Throwable): Unit = {
                // error happened, so signaling both the main thread that it should stop
                // and the downstream consumer of the error
                upstreamPromise.trySuccess(Cancel)
                o.onError(ex)
              }

              def onComplete(): Unit = {
                // NOTE: we aren't sending this onComplete signal downstream to our observer
                // instead we are just instructing upstream to send the next observable
                upstreamPromise.trySuccess(Continue)
                refID.cancel()
              }
            })

            upstreamPromise.future
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) {
                o.onError(ex)
                Cancel
              }
              else {
                Future.failed(ex)
              }
          }
        }

        def onError(ex: Throwable) = {
          // oops, error happened on main thread, piping that along should cancel everything
          o.onError(ex)
        }

        def onComplete() = {
          refCount.cancel()
        }
      })
    }

  /**
   * Implementation for [[Observable.flatScanDelayError]].
   */
  def delayError[T,R](source: Observable[T], initial: R)(op: (R, T) => Observable[R]) =
    Observable.unsafeCreate[R] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var state = initial
        private[this] val errors = mutable.ArrayBuffer.empty[Throwable]
        private[this] val refCount = RefCountCancelable {
          if (errors.nonEmpty)
            subscriber.onError(CompositeException(errors))
          else
            subscriber.onComplete()
        }

        def onNext(elem: T) = {
          // for protecting user calls
          var streamError = true
          try {
            val upstreamPromise = Promise[Ack]()
            val newState = op(state, elem)
            streamError = false

            val refID = refCount.acquire()

            newState.unsafeSubscribeFn(new Observer[R] {
              def onNext(elem: R): Future[Ack] = {
                state = elem
                subscriber.onNext(elem)
                  .ifCancelTryCanceling(upstreamPromise)
              }

              def onError(ex: Throwable): Unit = {
                errors += ex
                // next element please
                upstreamPromise.trySuccess(Continue)
                refID.cancel()
              }

              def onComplete(): Unit = {
                // next element please
                upstreamPromise.trySuccess(Continue)
                refID.cancel()
              }
            })

            upstreamPromise.future
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) {
                onError(ex)
                Cancel
              }
              else {
                Future.failed(ex)
              }
          }
        }

        def onError(ex: Throwable) = {
          errors += ex
          refCount.cancel()
        }

        def onComplete() = {
          refCount.cancel()
        }
      })
    }
}
