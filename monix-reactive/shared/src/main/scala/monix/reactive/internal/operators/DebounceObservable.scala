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
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.duration.FiniteDuration

private[reactive] final
class DebounceObservable[A](source: Observable[A], timeout: FiniteDuration, repeat: Boolean)
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = MultiAssignmentCancelable()
    val mainTask = SingleAssignmentCancelable()
    val composite = CompositeCancelable(mainTask, task)

    mainTask := source.unsafeSubscribeFn(new Subscriber.Sync[A] with Runnable { self =>
      implicit val scheduler = out.scheduler

      private[this] val timeoutMillis = timeout.toMillis
      private[this] var isDone = false
      private[this] var lastEvent: A = _
      private[this] var lastTSInMillis: Long = 0L
      private[this] var hasValue = false

      locally {
        scheduleNext(timeoutMillis)
      }

      def scheduleNext(delayMillis: Long): Unit = {
        // No need to synchronize this assignment, since we have a
        // happens-before relationship between scheduleOnce invocations.
        task := scheduler.scheduleOnce(delayMillis, TimeUnit.MILLISECONDS, self)
      }

      def run(): Unit = self.synchronized {
        if (!isDone) {
          if (lastEvent == null || !hasValue) {
            // in this case, either the data source hasn't emitted anything
            // yet (lastEvent == null), or we don't have a new value since
            // the last time we've tried (!hasValue), so keep waiting
            scheduleNext(timeoutMillis)
          } else {
            val rightNow = scheduler.currentTimeMillis()
            val sinceLastOnNext = rightNow - lastTSInMillis

            if (sinceLastOnNext >= timeoutMillis) {
              // signaling event, so next time we'll
              // require a new event
              hasValue = repeat

              out.onNext(lastEvent).syncFlatMap {
                case Continue =>
                  val executionTime = scheduler.currentTimeMillis() - rightNow
                  val delay = if (timeoutMillis > executionTime)
                    timeoutMillis - executionTime else 0L

                  scheduleNext(delay)
                  Continue

                case Stop =>
                  self.synchronized {
                    isDone = true
                    mainTask.cancel()
                  }
                  Stop
              }
            }
            else {
              val remainingTime = timeoutMillis - sinceLastOnNext
              scheduleNext(remainingTime)
            }
          }
        }
      }

      def onNext(elem: A): Ack = self.synchronized {
        if (!isDone) {
          lastEvent = elem
          lastTSInMillis = scheduler.currentTimeMillis()
          hasValue = true
          Continue
        } else {
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

      def onComplete(): Unit =
        self.synchronized {
          if (!isDone) {
            isDone = true
            task.cancel()
            out.onComplete()
          }
        }
    })

    composite
  }
}