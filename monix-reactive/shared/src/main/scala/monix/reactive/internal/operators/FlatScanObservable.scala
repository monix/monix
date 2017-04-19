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

package monix.reactive.internal.operators

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.{CompositeCancelable, MultiAssignmentCancelable}
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.exceptions.CompositeException
import monix.reactive.observers.Subscriber

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

private[reactive] final
class FlatScanObservable[A,R](
  source: Observable[A], initial: () => R, f: (R,A) => Observable[R], delayErrors: Boolean)
  extends Observable[R] {

  def unsafeSubscribeFn(out: Subscriber[R]): Cancelable = {
    var streamErrors = true
    try {
      val value = initial()
      streamErrors = false
      subscribeWithState(out, value)
    } catch {
      case NonFatal(ex) if streamErrors =>
        out.onError(ex)
        Cancelable.empty
    }
  }

  def subscribeWithState(out: Subscriber[R], initial: R): Cancelable = {
    val conn = MultiAssignmentCancelable()
    val composite = CompositeCancelable(conn)

    composite += source.unsafeSubscribeFn(new Subscriber[A] { self =>
      implicit val scheduler = out.scheduler

      private[this] var isDone = false
      private[this] var currentState = initial
      private[this] var upstreamWantsOnError = false
      private[this] var upstreamAck: Future[Ack] = Continue
      private[this] val errors = if (delayErrors)
        mutable.ArrayBuffer.empty[Throwable] else null

      def onNext(elem: A): Future[Ack] = {
        val upstreamPromise = Promise[Ack]()

        // Protects calls to user code from within the operator and
        // stream the error downstream if it happens, but if the
        // error happens because of calls to `onNext` or other
        // protocol calls, then the behavior should be undefined.
        var streamError = true
        upstreamAck = try {
          val newStateObservable = f(currentState, elem)
          streamError = false

          conn := newStateObservable.unsafeSubscribeFn(new Subscriber[R] {
            implicit val scheduler = out.scheduler
            private[this] var childAck: Future[Ack] = Continue

            def onNext(elem: R): Future[Ack] = {
              if (upstreamWantsOnError) {
                upstreamPromise.trySuccess(Continue)
                childAck = Stop
              } else {
                currentState = elem
                childAck = out.onNext(elem).syncOnStopFollow(upstreamPromise, Stop)
              }

              childAck
            }


            def onError(ex: Throwable): Unit = {
              if (delayErrors) {
                errors += ex
                onComplete()
              } else {
                // Error happened, so signaling both the main thread that
                // it should stop and the downstream consumer of the error
                upstreamPromise.trySuccess(Stop)
                self.signalOnError(ex)
              }
            }

            def onComplete(): Unit = {
              // NOTE: we aren't sending this onComplete signal downstream
              // instead we are just instructing upstream to send the next observable.
              // We also need to apply back-pressure on the last ack,
              // because otherwise we'll break back-pressure
              childAck.syncOnContinueFollow(upstreamPromise, Continue)
            }
          })

          upstreamPromise.future.syncTryFlatten
        } catch {
          case NonFatal(ex) if streamError =>
            onError(ex)
            Stop
        }

        upstreamAck
      }

      def signalOnError(ex: Throwable): Unit =
        self.synchronized {
          if (!isDone) {
            isDone = true
            out.onError(ex)
          } else {
            scheduler.reportFailure(ex)
          }
        }

      def onError(ex: Throwable): Unit =
        if (delayErrors) {
          errors += ex
          onComplete()
        } else {
          upstreamWantsOnError = true
          upstreamAck.syncOnContinue(signalOnError(ex))
        }

      def onComplete(): Unit =
        upstreamAck.syncOnContinue {
          if (!isDone) {
            isDone = true
            if (delayErrors && errors.nonEmpty)
              out.onError(CompositeException.build(errors))
            else
              out.onComplete()
          }
        }
    })
  }
}
