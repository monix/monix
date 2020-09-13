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

package monix.eval.internal

import monix.eval.Task
import monix.execution.rstreams.Subscription
import monix.execution.{Callback, Scheduler, UncaughtExceptionReporter}
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
          private[this] val conn = TaskConnection()
          private[this] val context =
            Task.Context(s, Task.defaultOptions.withSchedulerFeatures, conn, new StackTracedContext)

          def request(n: Long): Unit = {
            require(n > 0, "n must be strictly positive, according to the Reactive Streams contract, rule 3.9")
            if (isActive) {
              Task.unsafeStartEnsureAsync[A](self, context, new PublisherCallback(out))
            }
          }

          def cancel(): Unit = {
            isActive = false
            conn.cancel.runAsyncAndForget
          }
        })
      }
    }

  private final class PublisherCallback[A](out: Subscriber[_ >: A])(implicit s: UncaughtExceptionReporter)
    extends Callback[Throwable, A] {
    private[this] var isActive = true

    def onError(e: Throwable): Unit =
      if (isActive) {
        isActive = false
        out.onError(e)
      } else {
        s.reportFailure(e)
      }

    def onSuccess(value: A): Unit =
      if (isActive) {
        isActive = false
        out.onNext(value)
        out.onComplete()
      }
  }
}
