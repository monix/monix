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
import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.{CompositeCancelable, MultiAssignmentCancelable, SingleAssignmentCancelable}
import monix.execution.exceptions.UpstreamTimeoutException
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[reactive] final class UpstreamTimeoutObservable[+A](
  source: Observable[A], timeout: FiniteDuration)
  extends Observable[A] {

  def unsafeSubscribeFn(downstream: Subscriber[A]): Cancelable = {
    val timeoutCheck = MultiAssignmentCancelable()
    val mainTask = SingleAssignmentCancelable()
    val composite = CompositeCancelable(mainTask, timeoutCheck)

    mainTask := source.unsafeSubscribeFn(new Subscriber[A] with Runnable { self =>
      implicit val scheduler: Scheduler = downstream.scheduler

      private[this] val timeoutMillis = timeout.toMillis
      // MUST BE synchronized by `self`
      private[this] var isProcessingOnNext = false
      // MUST BE synchronized by `self`
      private[this] var isDone = false
      // MUST BE synchronized by `self`
      private[this] var lastEmittedMillis: Long =
        scheduler.currentTimeMillis()

      locally {
        timeoutCheck := scheduler.scheduleOnce(timeout.length, timeout.unit, self)
      }

      def run(): Unit = self.synchronized {
        if (!isDone) {
          val rightNow = scheduler.currentTimeMillis()
          val sinceLastOnNextInMillis =
            if (isProcessingOnNext) 0L else rightNow - lastEmittedMillis

          if (sinceLastOnNextInMillis >= timeoutMillis) {
            // Oops, timeout happened, triggering error.
            triggerTimeout()
          }
          else {
            val remainingTimeMillis = timeoutMillis - sinceLastOnNextInMillis
            // No need for synchronization or ordering on this assignment, since
            // there is a clear happens-before relationship between invocations
            timeoutCheck := scheduler.scheduleOnce(remainingTimeMillis, TimeUnit.MILLISECONDS, self)
          }
        }
      }

      def onNext(elem: A): Future[Ack] = {
        def unfreeze(): Ack = {
          lastEmittedMillis = scheduler.currentTimeMillis()
          isProcessingOnNext = false
          Continue
        }

        // Unfortunately we can't get away from this synchronization
        // as the scheduler is contending on `isDone` and if
        // we don't synchronize, then we can break the contract by sending
        // an `onNext` concurrently with an `onError` :-(
        self.synchronized {
          if (isDone) Stop else {
            isProcessingOnNext = true
            // Shenanigans for avoiding an unnecessary synchronize
            downstream.onNext(elem) match {
              case Continue => unfreeze()
              case Stop =>
                timeoutCheck.cancel()
                Stop

              case async =>
                async.flatMap {
                  case Continue => self.synchronized(unfreeze())
                  case Stop =>
                    timeoutCheck.cancel()
                    Stop
                }
            }
          }
        }
      }

      def triggerTimeout(): Unit = self.synchronized {
        if (!isDone) {
          isDone = true
          val ex = UpstreamTimeoutException(timeout)
          try downstream.onError(ex) finally
            mainTask.cancel()
        }
      }

      def onError(ex: Throwable): Unit = self.synchronized {
        if (!isDone) {
          isDone = true
          try downstream.onError(ex) finally
            timeoutCheck.cancel()
        }
      }

      def onComplete(): Unit = self.synchronized {
        if (!isDone) {
          isDone = true
          try downstream.onComplete() finally
            timeoutCheck.cancel()
        }
      }
    })

    composite
  }
}
