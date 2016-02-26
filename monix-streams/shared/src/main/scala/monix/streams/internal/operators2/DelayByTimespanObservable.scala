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

import java.util.concurrent.TimeUnit

import monix.execution.Ack.{Cancel, Continue}
import monix.execution.cancelables.{CompositeCancelable, MultiAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.streams.Observable
import monix.streams.observers.Subscriber
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

private[streams] final
class DelayByTimespanObservable[A](source: Observable[A], delay: FiniteDuration)
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = MultiAssignmentCancelable()
    val composite = CompositeCancelable(task)

    composite += source.unsafeSubscribeFn(new Subscriber[A] with Runnable { self =>
      implicit val scheduler = out.scheduler
      private[this] var isDone = false
      private[this] var completeTriggered = false
      private[this] val delayMs = delay.toMillis
      private[this] var currentElem: A = _
      private[this] var ack: Promise[Ack] = null

      def onNext(elem: A): Future[Ack] = {
        currentElem = elem
        ack = Promise()
        task := scheduler.scheduleOnce(delayMs, TimeUnit.MILLISECONDS, self)
        ack.future
      }

      def onError(ex: Throwable): Unit =
        self.synchronized {
          if (!isDone) {
            isDone = true
            try out.onError(ex) finally {
              if (ack != null) ack.trySuccess(Cancel)
              task.cancel()
            }
          }
        }

      def onComplete(): Unit = {
        completeTriggered = true
        val lastAck = if (ack eq null) Continue else ack.future

        lastAck.syncTryFlatten.syncOnContinue {
          if (!isDone) {
            isDone = true
            out.onComplete()
          }
        }
      }

      def run(): Unit = self.synchronized {
        if (!isDone) {
          val next = out.onNext(currentElem)

          if (completeTriggered) {
            isDone = true
            out.onComplete()
          }

          next match {
            case Continue => ack.success(Continue)
            case Cancel => ack.success(Cancel)
            case async => ack.completeWith(async)
          }
        }
      }
    })
  }
}
