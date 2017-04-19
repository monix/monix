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

import monix.execution.Ack.Stop
import monix.execution.cancelables.SerialCancelable
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final
class RestartUntilObservable[A](source: Observable[A], p: A => Boolean)
  extends Observable[A] { self =>

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val conn = SerialCancelable()
    loop(out, conn)
    conn
  }

  def loop(out: Subscriber[A], subscription: SerialCancelable): Unit = {
    // Needs synchronization because we can have a race condition
    // at least on the assignment of the cancelable.
    synchronized {
      subscription := source.unsafeSubscribeFn(new Subscriber[A] {
        implicit val scheduler: Scheduler = out.scheduler
        private[this] var isValidated = false
        private[this] var isDone = false

        def onNext(elem: A): Future[Ack] = {
          // Stream was validated, so we can just stream the event.
          if (isValidated) out.onNext(elem) else {
            // Protects calls to user code from within the operator and
            // stream the error downstream if it happens, but if the
            // error happens because of calls to `onNext` or other
            // protocol calls, then the behavior should be undefined.
            var streamErrors = true
            try {
              isValidated = p(elem)
              streamErrors = false

              if (isValidated) out.onNext(elem) else {
                // Oh noes, we have to resubscribe.
                // First we make sure no other events can happen
                isDone = true

                // Then we force an asynchronous boundary and retry
                out.scheduler.execute(new Runnable {
                  def run(): Unit = loop(out, subscription)
                })

                // Signal the current upstream to stop.
                // Current upstream will also be cancel when the
                // SerialCancelable gets assigned a new value.
                Stop
              }
            } catch {
              case NonFatal(ex) if streamErrors =>
                onError(ex)
                Stop
            }
          }
        }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            out.onError(ex)
          }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            out.onComplete()
          }
      })
    }
  }
}
