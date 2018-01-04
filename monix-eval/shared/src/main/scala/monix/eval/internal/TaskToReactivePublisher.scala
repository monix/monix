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

package monix.eval.internal

import monix.eval.{Callback, Task}
import monix.execution.Scheduler
import monix.execution.cancelables.StackedCancelable
import monix.execution.rstreams.Subscription
import org.reactivestreams.Subscriber

private[eval] object TaskToReactivePublisher {
  /**
    * Implementation for `Task.toReactivePublisher`
    */
  def apply[A](self: Task[A])(implicit s: Scheduler): org.reactivestreams.Publisher[A] =
    new org.reactivestreams.Publisher[A] {
      def subscribe(out: Subscriber[_ >: A]): Unit = {
        out.onSubscribe(new Subscription {
          private[this] var isActive = true
          private[this] val conn = StackedCancelable()
          private[this] val context = {
            val ref = Task.FrameIndexRef(s.executionModel)
            Task.Context(s, conn, ref, Task.defaultOptions)
          }

          def request(n: Long): Unit = {
            require(n > 0, "n must be strictly positive, according to " +
              "the Reactive Streams contract, rule 3.9")

            if (isActive) {
              Task.unsafeStartAsync[A](self, context,
                Callback.safe(new Callback[A] {
                  def onError(ex: Throwable): Unit =
                    out.onError(ex)

                  def onSuccess(value: A): Unit = {
                    out.onNext(value)
                    out.onComplete()
                  }
                }))
            }
          }

          def cancel(): Unit = {
            isActive = false
            conn.cancel()
          }
        })
      }
    }
}
