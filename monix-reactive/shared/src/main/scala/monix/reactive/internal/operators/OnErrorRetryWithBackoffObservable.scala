/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import monix.execution.Ack.Continue
import monix.execution.cancelables.OrderedCancelable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.{BackoffStrategy, Observable}
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[reactive] final class OnErrorRetryWithBackoffObservable[+A](source: Observable[A], maxRetries: Long, initialDelay: FiniteDuration, strategy: BackoffStrategy)
  extends Observable[A] { self =>

  private def loop(subscriber: Subscriber[A], task: OrderedCancelable, retryIdx: Long, currentDelay: FiniteDuration): Unit = {

    val cancelable = source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler: Scheduler = subscriber.scheduler

      private[this] var isDone = false
      private[this] var isBackoff = true
      private[this] var ack: Future[Ack] = Continue

      def onNext(elem: A): Future[Ack] = {
        isBackoff = false
        ack = subscriber.onNext(elem)
        ack
      }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          subscriber.onComplete()
        }

      def onError(ex: Throwable): Unit = {
        if (!isDone) {
          isDone = true

          if (maxRetries < 0 || retryIdx < maxRetries) {
            if (isBackoff) {
              scheduler.scheduleOnce(currentDelay) {
                ack.syncOnContinue(loop(subscriber, task, retryIdx + 1, strategy(retryIdx + 1, initialDelay, currentDelay)))
              }
            } else {
              scheduler.scheduleOnce(initialDelay) {
                ack.syncOnContinue(loop(subscriber, task, retryIdx = 0, strategy(0, initialDelay, initialDelay)))
              }
            }
          } else {
            subscriber.onError(ex)
          }

          List(0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1)
          List(0, 1, 0, 1, 0, 1, 0, 1, 0, 1)
        }
      }
    })

    // We need to do an `orderedUpdate`, because `onError` might have
    // already executed and we might be resubscribed by now.
    task.orderedUpdate(cancelable, retryIdx)
  }

  /** Characteristic function for an `Observable` instance, that creates
    * the subscription and that eventually starts the streaming of
    * events to the given [[monix.reactive.Observer]], to be provided by observable
    * implementations.
    *
    * $unsafeSubscribe
    *
    * $unsafeBecauseImpure
    */
  override def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    val task = OrderedCancelable()
    loop(subscriber, task, retryIdx = 0, initialDelay)
    task
  }
}
