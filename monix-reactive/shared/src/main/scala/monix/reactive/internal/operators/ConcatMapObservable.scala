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
import monix.execution.atomic.Atomic
import monix.execution.cancelables.MultiAssignmentCancelable
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.exceptions.CompositeException
import monix.reactive.observers.Subscriber
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

/** Implementation for `Observable.concatMap` (i.e. `flatMap`). */
private[reactive] final class ConcatMapObservable[A, B]
  (source: Observable[A], f: A => Observable[B], delayErrors: Boolean)
  extends Observable[B] {

  import ConcatMapObservable.RefCount

  def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    val childSubscription = MultiAssignmentCancelable()

    val mainSubscription = source.unsafeSubscribeFn(new Subscriber[A] { self =>
      implicit val scheduler = out.scheduler

      private[this] var isDone = false
      private[this] var upstreamWantsOnError = false
      private[this] var upstreamAck: Future[Ack] = Continue
      private[this] val errors = if (delayErrors)
        mutable.ArrayBuffer.empty[Throwable] else null

      // To be canceled once both the main stream is complete AND the
      // active child is complete. Helps to signal `onComplete` earlier.
      private[this] val refCount = new RefCount(() => {
        if (!isDone) {
          isDone = true
          if (delayErrors && errors.nonEmpty)
            out.onError(CompositeException(errors))
          else
            out.onComplete()
        }
      })

      def onNext(a: A): Future[Ack] = {
        val upstreamPromise = Promise[Ack]()

        // Protects calls to user code from within the operator and
        // stream the error downstream if it happens, but if the
        // error happens because of calls to `onNext` or other
        // protocol calls, then the behavior should be undefined.
        var streamError = true
        upstreamAck = try {
          val fb = f(a)
          streamError = false
          refCount.acquire()

          childSubscription := fb.unsafeSubscribeFn(new Subscriber[B] {
            implicit val scheduler = out.scheduler
            private[this] var childAck: Future[Ack] = Continue

            def onNext(elem: B): Future[Ack] = {
              if (upstreamWantsOnError) {
                upstreamPromise.trySuccess(Continue)
                childAck = Stop
              } else {
                childAck = out.onNext(elem)
                  .syncOnStopFollow(upstreamPromise, Stop)
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
              refCount.release()
              childAck.value match {
                case Some(v) =>
                  upstreamPromise.tryComplete(v)
                case None =>
                  upstreamPromise.tryCompleteWith(childAck)
              }
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
        refCount.release()
    })

    Cancelable { () =>
      childSubscription.cancel()
      mainSubscription.cancel()
    }
  }
}

private[reactive] object ConcatMapObservable {
  /** Constant used as a state in [[RefCount]] */
  private final val CANCELLED = 0
  /** Constant used as a state in [[RefCount]] */
  private final val IS_CHILDLESS = 1
  /** Constant used as a state in [[RefCount]] */
  private final val HAS_ONE_CHILD = 2

  /** Lighter replacement for [[monix.execution.cancelables.RefCountCancelable]]. */
  private final class RefCount(onCancel: () => Unit) {
    private[this] var thunk = onCancel
    private[this] val stateRef = Atomic(IS_CHILDLESS)

    def acquire(): Unit = {
      val prev = stateRef.getAndSet(HAS_ONE_CHILD)
      if (prev != IS_CHILDLESS)
        throw new IllegalStateException(s"stateRef was $prev")
    }

    @tailrec def release(): Unit =
      stateRef.get match {
        case CANCELLED =>
          () // do nothing else
        case IS_CHILDLESS =>
          if (stateRef.compareAndSet(IS_CHILDLESS, CANCELLED))
            try thunk() finally { thunk = null }
          else
            release()
        case _ => // HAS_CHILD
          if (!stateRef.compareAndSet(HAS_ONE_CHILD, IS_CHILDLESS))
            release()
      }
  }
}