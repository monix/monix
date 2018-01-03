/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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
import monix.execution.cancelables.{CompositeCancelable, SingleAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] final class ThrottleLastObservable[+A, S](
  source: Observable[A], sampler: Observable[S],
  shouldRepeatOnSilence: Boolean)
  extends Observable[A] {

  def unsafeSubscribeFn(downstream: Subscriber[A]): Cancelable = {
    val upstreamSubscription = SingleAssignmentCancelable()
    val samplerSubscription = SingleAssignmentCancelable()
    val composite = CompositeCancelable(upstreamSubscription, samplerSubscription)

    upstreamSubscription := source.unsafeSubscribeFn(
      new Subscriber.Sync[A] { upstreamSubscriber =>
        implicit val scheduler = downstream.scheduler

        // Value is volatile to keep write to lastValue visible
        // after this one is seen as being true
        @volatile private[this] var hasValue = false
        // MUST BE written before `hasValue = true`
        private[this] var lastValue: A = _
        // To be written in onComplete/onError, to be read from tick
        private[this] var upstreamIsDone = false
        // MUST BE synchronized by `upstreamSubscriber`.
        private[this] var downstreamIsDone = false

        def onNext(elem: A): Ack =
          if (downstreamIsDone) Stop else {
            lastValue = elem
            hasValue = true
            Continue
          }

        def onError(ex: Throwable): Unit =
          upstreamSubscriber.synchronized {
            if (!downstreamIsDone) {
              downstreamIsDone = true
              samplerSubscription.cancel()
              downstream.onError(ex)
            }
          }

        def onComplete(): Unit =
          upstreamSubscriber.synchronized {
            upstreamIsDone = true
          }

        samplerSubscription := sampler.unsafeSubscribeFn(new Subscriber[S] { self =>
          implicit val scheduler = downstream.scheduler

          def onNext(elem: S): Future[Ack] =
            upstreamSubscriber.synchronized(signalNext())

          def onError(ex: Throwable): Unit =
            upstreamSubscriber.onError(ex)

          def onComplete(): Unit =
            upstreamSubscriber.synchronized {
              upstreamIsDone = true
              signalNext()
            }

          def signalNext(): Future[Ack] =
            if (downstreamIsDone) Stop else {
              val next = if (!hasValue) Continue else {
                hasValue = shouldRepeatOnSilence
                val ack = downstream.onNext(lastValue)
                ack.syncOnStopOrFailure { _ =>
                  downstreamIsDone = true
                  upstreamSubscription.cancel()
                }
              }

              if (!upstreamIsDone) next else {
                downstreamIsDone = true
                upstreamSubscription.cancel()
                if (next ne Stop) downstream.onComplete()
                Stop
              }
            }
        })
      })

    composite
  }
}
