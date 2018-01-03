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

package monix.reactive.internal.consumers

import monix.eval.{Callback, Task}
import monix.execution.Scheduler
import monix.execution.cancelables.AssignableCancelable
import monix.execution.misc.NonFatal
import monix.reactive.Consumer
import monix.reactive.observers.Subscriber

/** Implementation for [[monix.reactive.Consumer.mapTask]]. */
private[reactive]
final class MapTaskConsumer[In, R, R2](source: Consumer[In,R], f: R => Task[R2])
  extends Consumer[In, R2] {

  def createSubscriber(cb: Callback[R2], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
    val asyncCallback = new Callback[R] {
      def onSuccess(value: R): Unit =
        s.execute(new Runnable {
          // Forcing async boundary, otherwise we might
          // end up with stack-overflows or other problems
          def run(): Unit = {
            implicit val scheduler = s
            // For protecting the contract, as if a call was already made to
            // `onSuccess`, then we can't call `onError`
            var streamErrors = true
            try {
              val task = f(value)
              streamErrors = false
              task.runAsync(cb)
            } catch {
              case NonFatal(ex) =>
                if (streamErrors) cb.onError(ex)
                else s.reportFailure(ex)
            }
          }
        })

      def onError(ex: Throwable): Unit = {
        // Forcing async boundary, otherwise we might
        // end up with stack-overflows or other problems
        s.execute(new Runnable { def run(): Unit = cb.onError(ex) })
      }
    }

    source.createSubscriber(asyncCallback, s)
  }
}
