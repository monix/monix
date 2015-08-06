/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

package monifu.reactive.operators

import monifu.concurrent.cancelables.MultiAssignmentCancelable
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.{Ack, Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._

object debounce {
  /**
   * Only emit an item from an Observable if a particular 
   * timespan has passed without it emitting another item
   */
  def apply[T](source: Observable[T], timeout: FiniteDuration): Observable[T] = {
    Observable.create { subscriber => 
      implicit val s = subscriber.scheduler
      val downstream = subscriber.observer
      val timeoutNanos = timeout.toNanos

      source.unsafeSubscribe(new Observer[T] with Runnable { self =>
        private[this] val task = MultiAssignmentCancelable()
        private[this] var ack: Future[Ack] = Continue
        private[this] var isDone = false
        @volatile private[this] var lastEvent: (T, Long) = _

        locally {
          scheduleNext(timeout)
        }

        def scheduleNext(delay: FiniteDuration): Unit = {
          task := s.scheduleOnce(delay, self)
        }

        def run(): Unit = self.synchronized {
          if (!isDone) {
            if (lastEvent == null) scheduleNext(timeout) else {
              val (lastEmitted, lastTS) = lastEvent
              val rightNow = s.nanoTime()
              val sinceLastOnNext = (rightNow - lastTS).nanos

              if (sinceLastOnNext >= timeout) {
                ack = downstream.onNext(lastEmitted).continueWith {
                  val executionTime = s.nanoTime() - rightNow
                  val delay = if (timeoutNanos > executionTime)
                    timeoutNanos - executionTime else 0L

                  scheduleNext(delay.nanos)
                  Continue
                }
              }
              else {
                val remainingTime = timeout - sinceLastOnNext
                scheduleNext(remainingTime)
              }
            }
          }
        }

        def onNext(elem: T) = {
          lastEvent = (elem, s.nanoTime())
          Continue
        }
        
        def onError(ex: Throwable): Unit = 
          self.synchronized {
            if (!isDone) {
              isDone = true
              task.cancel()
              ack.onContinueSignalError(downstream, ex)
              ack = Cancel
            }
          }

        def onComplete(): Unit = 
          self.synchronized {
            if (!isDone) {
              isDone = true
              task.cancel()
              ack.onContinueSignalComplete(downstream)
              ack = Cancel
            }
          }
      })
    }
  }
}
