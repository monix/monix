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

package monix.streams.internal.operators2

import monix.execution.Ack
import monix.execution.Ack.{Cancel, Continue}
import monix.execution.cancelables.RefCountCancelable
import monix.streams.exceptions.CompositeException
import monix.streams.observables.ProducerLike.Operator
import monix.streams.observers.Subscriber
import monix.streams.{CanObserve, Observer}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.language.higherKinds
import scala.util.control.NonFatal

/** Implementation for [[monix.streams.observables.ProducerLike.concatMap2]] */
private[streams] final class OperatorConcatMap[A, B, F[_] : CanObserve]
  (f: A => F[B], delayErrors: Boolean)
  extends Operator[A, B] {

  def apply(out: Subscriber[B]): Subscriber[A] = {
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] val converter = implicitly[CanObserve[F]]
      private[this] val errors = if (delayErrors)
        mutable.ArrayBuffer.empty[Throwable] else null

      private[this] val refCount = RefCountCancelable {
        if (delayErrors && errors.nonEmpty)
          out.onError(CompositeException(errors))
        else
          out.onComplete()
      }

      def onNext(a: A): Future[Ack] = {
        val upstreamPromise = Promise[Ack]()
        val refID = refCount.acquire()

        // Protects calls to user code from within the operator and
        // stream the error downstream if it happens, but if the
        // error happens because of calls to `onNext` or other
        // protocol calls, then the behavior should be undefined.
        var streamError = true
        try {
          val fb = f(a)
          streamError = false

          converter.observable(fb).unsafeSubscribeFn(new Observer[B] {
            def onNext(elem: B): Future[Ack] =
              out.onNext(elem).syncOnCancelOrFailure {
                upstreamPromise.trySuccess(Cancel)
              }

            def onError(ex: Throwable): Unit = {
              if (delayErrors) {
                errors += ex
                onComplete()
              } else {
                // Error happened, so signaling both the main thread that
                // it should stop and the downstream consumer of the error
                upstreamPromise.trySuccess(Cancel)
                out.onError(ex)
              }
            }

            def onComplete(): Unit = {
              // NOTE: we aren't sending this onComplete signal downstream to our observerU
              // instead we are just instructing upstream to send the next observable
              upstreamPromise.trySuccess(Continue)
              refID.cancel()
            }
          })

          upstreamPromise.future
        } catch {
          case NonFatal(ex) if streamError =>
            onError(ex)
            Cancel
        }
      }

      def onError(ex: Throwable): Unit = {
        if (delayErrors) {
          errors += ex
          onComplete()
        } else {
          // Oops, error happened on main thread, piping that
          // along should cancel everything
          out.onError(ex)
        }
      }

      def onComplete(): Unit = {
        refCount.cancel()
      }
    }
  }
}
