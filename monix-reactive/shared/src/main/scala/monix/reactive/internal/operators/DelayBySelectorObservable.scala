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
import monix.reactive.observers.Subscriber

import scala.concurrent.{Future, Promise}

private[reactive] final
class DelayBySelectorObservable[A,S](source: Observable[A], selector: A => Observable[S])
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = MultiAssignmentCancelable()
    val composite = CompositeCancelable(task)

    composite += source.unsafeSubscribeFn(new Subscriber[A] { self =>
      implicit val scheduler = out.scheduler

      private[this] var completeTriggered = false
      private[this] var isDone = false
      private[this] var currentElem: A = _
      private[this] var ack: Promise[Ack] = null

      private[this] val trigger = new Subscriber.Sync[Any] {
        implicit val scheduler = out.scheduler
        def onNext(elem: Any): Ack = throw new IllegalStateException
        def onError(ex: Throwable): Unit = self.onError(ex)
        def onComplete(): Unit = self.sendOnNext()
      }

      def sendOnNext(): Unit = self.synchronized {
        if (!isDone) {
          val next = out.onNext(currentElem)
          if (completeTriggered) {
            isDone = true
            out.onComplete()
          }

          next match {
            case Continue => ack.success(Continue)
            case Stop => ack.success(Stop)
            case async => ack.completeWith(async)
          }
        }
      }

      def onNext(elem: A): Future[Ack] = {
        currentElem = elem
        ack = Promise()

        var streamErrors = true
        try {
          val obs = selector(elem).take(0)
          streamErrors = false
          task := obs.unsafeSubscribeFn(trigger)
          ack.future
        }
        catch {
          case NonFatal(ex) if streamErrors =>
            onError(ex)
            Stop
        }
      }

      def onError(ex: Throwable): Unit =
        self.synchronized {
          if (!isDone) {
            isDone = true
            task.cancel()
            out.onError(ex)
          }
        }

      def onComplete(): Unit = {
        completeTriggered = true
        ack.future.syncTryFlatten.syncOnContinue {
          if (!isDone) {
            isDone = true
            out.onComplete()
          }
        }
      }
    })
  }
}
