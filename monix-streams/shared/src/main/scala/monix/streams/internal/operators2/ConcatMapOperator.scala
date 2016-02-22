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
import monix.streams.ObservableLike.Operator
import monix.streams.exceptions.CompositeException
import monix.streams.observers.Subscriber
import monix.streams.{Observable, Observer}
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.language.higherKinds
import scala.util.control.NonFatal

private[streams] final
class ConcatMapOperator[A, B](f: A => Observable[B], delayErrors: Boolean)
  extends Operator[A, B] {

  def apply(out: Subscriber[B]): Subscriber[A] = {
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var ack: Future[Ack] = Continue
      private[this] val errors = if (delayErrors)
        mutable.ArrayBuffer.empty[Throwable] else null

      private[this] var isDone = false
      private[this] val refCount = RefCountCancelable {
        if (!isDone) {
          isDone = true
          if (delayErrors && errors.nonEmpty)
            out.onError(CompositeException(errors))
          else
            out.onComplete()
        }
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

          fb.unsafeSubscribeFn(new Observer[B] {
            def onNext(elem: B): Future[Ack] = {
              ack = out.onNext(elem).syncOnCancelFollow(upstreamPromise, Cancel)
              ack
            }

            def onError(ex: Throwable): Unit = {
              if (delayErrors) {
                errors += ex
                onComplete()
              } else {
                // Error happened, so signaling both the main thread that
                // it should stop and the downstream consumer of the error
                isDone = true
                upstreamPromise.trySuccess(Cancel)
                out.onError(ex)
              }
            }

            def onComplete(): Unit = {
              // NOTE: we aren't sending this onComplete signal downstream to our observerU
              // instead we are just instructing upstream to send the next observable.
              // We also need to apply back-pressure on the last ack,
              // because an onNext probably follows.
              ack.syncOnContinueFollow(upstreamPromise, Continue)
              refID.cancel()
            }
          })

          upstreamPromise.future.syncTryFlatten
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
        } else if (!isDone) {
          // Oops, error happened on main thread, piping that
          // along should cancel everything
          isDone = true
          out.onError(ex)
        }
      }

      def onComplete(): Unit = {
        refCount.cancel()
      }
    }
  }
}
