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
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.concurrent.CancellationException

private[reactive] final
class OnCancelTriggerErrorObservable[A](source: Observable[A])
  extends Observable[A] {

  def unsafeSubscribeFn(downstream: Subscriber[A]): Cancelable = {
    val out: Subscriber[A] = new Subscriber[A] { self =>
      implicit val scheduler = downstream.scheduler
      private[this] var isDone = false

      def onNext(elem: A): Future[Ack] =
        self.synchronized {
          if (isDone) Stop else
            stopStreamOnCancel(downstream.onNext(elem))
        }

      def onError(ex: Throwable): Unit =
        self.synchronized {
          if (!isDone) {
            isDone = true
            downstream.onError(ex)
          }
        }

      def onComplete(): Unit =
        self.synchronized {
          if (!isDone) {
            isDone = true
            downstream.onComplete()
          }
        }

      private def stopStreamOnCancel(ack: Future[Ack]): Future[Ack] = {
        if (ack eq Continue)
          Continue
        else if (ack eq Stop) {
          isDone = true
          Stop
        }
        else if (ack.isCompleted) {
          val sync = ack.value.get
          if (sync.isSuccess) stopStreamOnCancel(sync.get) else {
            isDone = true
            scheduler.reportFailure(sync.failed.get)
            Stop
          }
        }
        else {
          ack.onComplete(result => self.synchronized(stopStreamOnCancel(ack)))
          ack
        }
      }
    }

    val subscription = source.unsafeSubscribeFn(out)

    Cancelable(() => {
      try out.onError(new CancellationException("Connection was cancelled"))
      finally subscription.cancel()
    })
  }
}
