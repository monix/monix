/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.Scheduler
import monix.execution.cancelables.{ CompositeCancelable, MultiAssignCancelable, SingleAssignCancelable }
import monix.execution.{ Ack, Cancelable }
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[reactive] final class ThrottleLatestObservable[A](
  source: Observable[A],
  duration: FiniteDuration,
  emitLast: Boolean
) extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = MultiAssignCancelable()
    val mainTask = SingleAssignCancelable()
    val composite = CompositeCancelable(mainTask, task)

    mainTask := source.unsafeSubscribeFn(new Subscriber[A] with Runnable {
      self =>
      implicit val scheduler: Scheduler = out.scheduler

      private val durationMilis = duration.toMillis
      private var isDone = false
      private var lastEvent: A = null.asInstanceOf[A]
      private var hasValue = false
      private var shouldEmitNext = true
      private var ack: Future[Ack] = null.asInstanceOf[Future[Ack]]

      def scheduleNext(delayMillis: Long): Unit = {
        // No need to synchronize this assignment, since we have a
        // happens-before relationship between scheduleOnce invocations.
        task := scheduler.scheduleOnce(delayMillis, TimeUnit.MILLISECONDS, self)
        ()
      }

      override def run(): Unit = self.synchronized {
        if (!isDone) {
          if (hasValue) {
            hasValue = false
            val now = scheduler.clockMonotonic(TimeUnit.MILLISECONDS)
            ack = out.onNext(lastEvent)
            val _ = ack.syncFlatMap {
              case Continue =>
                val elapsed = scheduler.clockMonotonic(TimeUnit.MILLISECONDS) - now
                val delay =
                  if (durationMilis > elapsed)
                    durationMilis - elapsed
                  else 0L
                scheduleNext(delay)
                Continue
              case Stop =>
                self.synchronized {
                  isDone = true
                  mainTask.cancel()
                }
                Stop
            }
          } else {
            shouldEmitNext = true
          }
        }
      }

      override def onNext(elem: A): Future[Ack] = self.synchronized {
        if (!isDone) {
          if (shouldEmitNext) {
            hasValue = false
            shouldEmitNext = false
            ack = out.onNext(elem)
            scheduleNext(durationMilis)
            ack
          } else {
            lastEvent = elem
            hasValue = true
            Continue
          }
        } else {
          Stop
        }
      }

      override def onError(ex: Throwable): Unit = self.synchronized {
        if (!isDone) {
          isDone = true
          out.onError(ex)
          task.cancel()
        }
      }

      override def onComplete(): Unit = self.synchronized {
        if (!isDone) {
          val lastAck = if (ack == null) Continue else ack
          val _ = lastAck.syncTryFlatten.syncOnContinue { signalOnComplete() }
        }
      }

      private def signalOnComplete(): Unit = {
        if (emitLast && hasValue) {
          val _ = out.onNext(lastEvent).syncTryFlatten.syncOnContinue {
            isDone = true
            out.onComplete()
            task.cancel()
          }
        } else {
          isDone = true
          out.onComplete()
          task.cancel()
        }
      }
    })

    composite
  }
}
