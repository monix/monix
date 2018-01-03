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

import java.util.concurrent.TimeUnit

import monix.execution.Ack.{Stop, Continue}
import monix.execution.cancelables.{CompositeCancelable, MultiAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.execution.atomic.Atomic
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

private[reactive] final
class DelayByTimespanObservable[A](source: Observable[A], delay: FiniteDuration)
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = MultiAssignmentCancelable()
    val composite = CompositeCancelable(task)

    composite += source.unsafeSubscribeFn(new Subscriber[A] with Runnable { self =>
      implicit val scheduler = out.scheduler
      private[this] var hasError = false
      private[this] val isDone = Atomic(false)
      private[this] var completeTriggered = false
      private[this] val delayMs = delay.toMillis
      private[this] var currentElem: A = _
      private[this] var ack: Promise[Ack] = _

      def onNext(elem: A): Future[Ack] = {
        currentElem = elem
        ack = Promise()
        task := scheduler.scheduleOnce(delayMs, TimeUnit.MILLISECONDS, self)
        ack.future
      }

      // Method `onComplete` is ordered to execute after run() by means of
      // `ack`, however access to `completeTriggered` is concurrent,
      // but that's OK, because it's a shortcut meant to finish the stream
      // earlier if possible.
      def onComplete(): Unit = {
        completeTriggered = true
        val lastAck = if (ack eq null) Continue else ack.future

        lastAck.syncTryFlatten.syncOnContinue {
          if (!isDone.getAndSet(true)) out.onComplete()
        }
      }

      // Method `onError` is concurrent with run(), so we need to synchronize
      // in order to avoid a breach of the contract
      def onError(ex: Throwable): Unit =
        self.synchronized {
          if (!isDone.getAndSet(true)) {
            hasError = true
            try out.onError(ex) finally {
              if (ack != null) ack.trySuccess(Stop)
              task.cancel()
            }
          }
        }

      // Method `run` needs to be synchronized because it is concurrent
      // with `onError`.
      def run(): Unit = self.synchronized {
        if (!hasError) {
          val next = out.onNext(currentElem)

          if (completeTriggered && !isDone.getAndSet(true))
            out.onComplete()

          next match {
            case Continue => ack.success(Continue)
            case Stop => ack.success(Stop)
            case async => ack.completeWith(async)
          }
        }
      }
    })
  }
}
