/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.execution.cancelables.{CompositeCancelable, MultiAssignCancelable, SingleAssignCancelable}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import java.util.concurrent.TimeUnit
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

private[reactive] final class ThrottleLatestObservable[A](source: Observable[A], duration: FiniteDuration, emitLast: Boolean)
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val task = MultiAssignCancelable()
    val mainTask = SingleAssignCancelable()
    val composite = CompositeCancelable(mainTask, task)

    mainTask := source.unsafeSubscribeFn(new Subscriber[A] with Runnable {
      self =>
      implicit val scheduler = out.scheduler

      private[this] val durationMilis = duration.toMillis
      private[this] var isDone = false
      private[this] var lastEvent: A = _
      @volatile private[this] var hasValue = false
      @volatile private[this] var shouldEmitNext = true
      private [this] var lastAck: Future[Ack] = _

      def scheduleNext(delayMillis: Long): Unit = {
        // No need to synchronize this assignment, since we have a
        // happens-before relationship between scheduleOnce invocations.
        task := scheduler.scheduleOnce(delayMillis, TimeUnit.MILLISECONDS, self)
        ()
      }


      override def run(): Unit = self.synchronized{
        if(!isDone) {
          if(hasValue) {
            hasValue = false
            val now = scheduler.clockMonotonic(TimeUnit.SECONDS)

            out.onNext(lastEvent).syncFlatMap {
              case Continue =>
                val elapsed = scheduler.clockMonotonic(TimeUnit.SECONDS) - now
                val delay = if(durationMilis > elapsed)
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
            ()
          } else {
            shouldEmitNext = true
          }
        }
      }

      override def onNext(elem: A): Future[Ack] = self.synchronized {
        if(!isDone) {
          if(shouldEmitNext) {
            hasValue = false
            shouldEmitNext = false
            val next = out.onNext(elem)
            scheduleNext(durationMilis)
            next
          } else {
            lastEvent = elem
            hasValue = true
            Continue
          }
        } else {
          Stop
        }
      }

      override def onError(ex: Throwable): Unit =  self.synchronized {
        if (!isDone) {
          isDone = true
          out.onError(ex)
          task.cancel()
        }
      }

      override def onComplete(): Unit =  self.synchronized {
        if (!isDone) {
          if(emitLast && hasValue) {
            out.onNext(lastEvent).syncTryFlatten.syncOnContinue{
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
        ()
      }
    })

    composite
  }
}
