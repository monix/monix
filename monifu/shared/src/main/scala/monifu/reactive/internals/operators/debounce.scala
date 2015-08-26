/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.internals.operators

import java.util.concurrent.TimeUnit
import monifu.concurrent.cancelables.MultiAssignmentCancelable
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.{Ack, Observable}
import scala.concurrent.Future
import scala.concurrent.duration._

private[reactive] object debounce {
  /**
   * Only emit an item from an Observable if a particular 
   * timespan has passed without it emitting another item
   */
  def apply[T](source: Observable[T], timeout: FiniteDuration): Observable[T] = {
    Observable.create { subscriber => 
      implicit val s = subscriber.scheduler
      val downstream = subscriber.observer
      val timeoutMillis = timeout.toMillis

      source.unsafeSubscribe(new SynchronousObserver[T] with Runnable { self =>
        private[this] val task = MultiAssignmentCancelable()
        private[this] var ack: Future[Ack] = Continue
        private[this] var isDone = false
        private[this] var lastEvent: T = _
        private[this] var lastTSInMillis: Long = 0L
        private[this] var hasValue = false

        locally {
          scheduleNext(timeoutMillis)
        }

        def scheduleNext(delayMillis: Long): Unit = {
          task := s.scheduleOnce(delayMillis, TimeUnit.MILLISECONDS, self)
        }

        def run(): Unit = self.synchronized {
          if (!isDone) {
            if (lastEvent == null || !hasValue) {
              // in this case, either the data source hasn't emitted anything
              // yet (lastEvent == null), or we don't have a new value since
              // the last time we've tried (!hasValue), so keep waiting
              scheduleNext(timeoutMillis)
            }
            else {
              val rightNow = s.currentTimeMillis()
              val sinceLastOnNext = rightNow - lastTSInMillis

              if (sinceLastOnNext >= timeoutMillis) {
                // signaling event, so next time we'll
                // require a new event
                hasValue = false

                ack = downstream.onNext(lastEvent).fastFlatMap {
                  case Continue =>
                    val executionTime = s.currentTimeMillis() - rightNow
                    val delay = if (timeoutMillis > executionTime)
                      timeoutMillis - executionTime else 0L

                    scheduleNext(delay)
                    Continue

                  case Cancel =>
                    self.synchronized { isDone = true }
                    Cancel
                }
              }
              else {
                val remainingTime = timeoutMillis - sinceLastOnNext
                scheduleNext(remainingTime)
              }
            }
          }
        }

        def onNext(elem: T): Ack = self.synchronized {
          if (!isDone) {
            lastEvent = elem
            lastTSInMillis = s.currentTimeMillis()
            hasValue = true
            Continue
          }
          else {
            Cancel
          }
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
