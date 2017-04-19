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
import monix.execution.Cancelable
import monix.execution.cancelables._
import monix.reactive.exceptions.CompositeException
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import monix.reactive.{Observable, OverflowStrategy}
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal

import scala.collection.mutable

private[reactive] final class MergeMapObservable[A,B](
  source: Observable[A],
  f: A => Observable[B],
  overflowStrategy: OverflowStrategy[B],
  delayErrors: Boolean)
  extends Observable[B] {

  def unsafeSubscribeFn(downstream: Subscriber[B]): Cancelable = {
    val composite = CompositeCancelable()

    composite += source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = downstream.scheduler
      private[this] val subscriberB: Subscriber[B] =
        BufferedSubscriber(downstream, overflowStrategy)

      private[this] val upstreamIsDone = Atomic(false)
      private[this] val errors = if (delayErrors)
        mutable.ArrayBuffer.empty[Throwable] else null

      private[this] val refCount = RefCountCancelable { () =>
        if (!upstreamIsDone.getAndSet(true)) {
          if (delayErrors)
            errors.synchronized {
              if (errors.nonEmpty)
                subscriberB.onError(CompositeException.build(errors))
              else
                subscriberB.onComplete()
            }
          else {
            subscriberB.onComplete()
          }
        }
      }

      private def cancelUpstream(): Stop = {
        if (!upstreamIsDone.getAndSet(true)) composite.cancel()
        Stop
      }

      def onNext(elem: A) = {
        // Protects calls to user code from within the operator and
        // stream the error downstream if it happens, but if the
        // error happens because of calls to `onNext` or other
        // protocol calls, then the behavior should be undefined.
        var streamError = true
        try {
          val fb = f(elem)
          streamError = false
          val refID = refCount.acquire()

          val childTask = SingleAssignmentCancelable()
          composite += childTask

          childTask := fb.unsafeSubscribeFn(new Subscriber[B] {
            implicit val scheduler = downstream.scheduler

            def onNext(elem: B) = {
              subscriberB.onNext(elem).syncOnStopOrFailure(_ => cancelUpstream())
            }

            def onError(ex: Throwable): Unit = {
              if (delayErrors) errors.synchronized {
                errors += ex
                refID.cancel()
              }
              else if (!upstreamIsDone.getAndSet(true)) {
                try subscriberB.onError(ex) finally
                  composite.cancel()
              }
            }

            def onComplete(): Unit = {
              // NOTE: we aren't sending this onComplete signal downstream to our observerU
              // we will eventually do that after all of them are complete
              refID.cancel()
              // GC purposes
              composite -= childTask
            }
          })

          Continue
        } catch {
          case NonFatal(ex) if streamError =>
            onError(ex)
            Stop
        }
      }

      def onError(ex: Throwable) = {
        if (delayErrors) errors.synchronized {
          errors += ex
          onComplete()
        }
        else if (!upstreamIsDone.getAndSet(true)) {
          // oops, error happened on main thread,
          // piping that along should cancel everything
          composite.cancel()
          subscriberB.onError(ex)
        }
      }

      def onComplete() = {
        refCount.cancel()
      }
    })

    composite
  }
}
