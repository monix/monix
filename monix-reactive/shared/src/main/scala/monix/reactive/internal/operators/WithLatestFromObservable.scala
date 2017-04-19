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
import monix.execution.cancelables.CompositeCancelable
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final class WithLatestFromObservable[A,B,+R](
  source: Observable[A], other: Observable[B], f: (A,B) => R)
  extends Observable[R] {

  def unsafeSubscribeFn(out: Subscriber[R]): Cancelable = {
    val connection = CompositeCancelable()

    connection += source.unsafeSubscribeFn(
      new Subscriber[A] { self =>
        implicit val scheduler = out.scheduler

        private[this] var isDone = false
        private[this] var otherStarted = false
        private[this] var lastOther: B = _

        private[this] val otherConnection = {
          val ref = other.unsafeSubscribeFn(
            new Subscriber.Sync[B] {
              implicit val scheduler = out.scheduler

              def onNext(elem: B): Ack =
                self.synchronized {
                  if (isDone) Stop else {
                    if (!otherStarted) otherStarted = true
                    lastOther = elem
                    Continue
                  }
                }

              def onComplete(): Unit = ()
              def onError(ex: Throwable): Unit =
                self.onError(ex)
            })

          connection += ref
          ref
        }

        def onNext(elem: A): Future[Ack] =
          self.synchronized {
            if (isDone) Stop
            else if (!otherStarted) Continue
            else {
              // Protects calls to user code from within the operator and
              // stream the error downstream if it happens, but if the
              // error happens because of calls to `onNext` or other
              // protocol calls, then the behavior should be undefined.
              var streamErrors = true
              try {
                val r = f(elem, lastOther)
                streamErrors = false
                out.onNext(r)
              } catch {
                case NonFatal(ex) if streamErrors =>
                  self.onError(ex)
                  Stop
              }
            }
          }

        def onError(ex: Throwable): Unit =
          signalComplete(ex)
        def onComplete(): Unit =
          signalComplete(null)

        private def signalComplete(ex: Throwable): Unit =
          self.synchronized {
            if (!isDone)
              try {
                isDone = true
                if (ex == null) out.onComplete()
                else out.onError(ex)
              } finally {
                otherConnection.cancel()
              }
          }
      })
  }
}