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

import monix.execution.Ack.Continue
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.execution.cancelables.MultiAssignmentCancelable
import monix.execution.misc.NonFatal
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.util.Success

private[reactive] final
class OnErrorRetryIfObservable[+A](source: Observable[A], p: Throwable => Boolean)
  extends Observable[A] {

  private def loop(subscriber: Subscriber[A], task: MultiAssignmentCancelable, retryIdx: Long): Unit = {
    val cancelable = source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler: Scheduler = subscriber.scheduler
      private[this] var isDone = false
      private[this] var ack: Future[Ack] = Continue

      def onNext(elem: A): Future[Ack] = {
        ack = subscriber.onNext(elem)
        ack
      }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          subscriber.onComplete()
        }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true

          // Protects calls to user code from within the operator and
          // stream the error downstream if it happens, but if the
          // error happens because of calls to `onNext` or other
          // protocol calls, then the behavior should be undefined.
          var streamError = true
          try {
            val shouldRetry = p(ex)
            streamError = false

            if (shouldRetry) {
              // need asynchronous execution to avoid a synchronous loop
              // blowing out the call stack
              ack.onComplete {
                case Success(Continue) =>
                  loop(subscriber, task, retryIdx+1)
                case _ =>
                  () // stop
              }
            } else {
              subscriber.onError(ex)
            }
          } catch {
            case NonFatal(err) if streamError =>
              scheduler.reportFailure(ex)
              subscriber.onError(err)
          }
        }
    })

    // We need to do an `orderedUpdate`, because `onError` might have
    // already executed and we might be resubscribed by now.
    task.orderedUpdate(cancelable, retryIdx)
  }

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    val task = MultiAssignmentCancelable()
    loop(subscriber, task, retryIdx = 0)
    task
  }
}
