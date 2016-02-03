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

package monix.streams.internal.operators

import java.util.concurrent.TimeUnit
import monix.execution.cancelables.MultiAssignmentCancelable
import monix.streams.observers.SyncObserver
import monix.streams.{Observable, Ack}
import monix.streams.Ack.{Cancel, Continue}
import monix.streams.internal._
import scala.concurrent.Future
import scala.concurrent.duration._

private[monix] object debounce {
  /**
    * Implementation for [[Observable.debounce]].
    */
  def timeout[T](source: Observable[T], timeout: FiniteDuration, repeat: Boolean): Observable[T] = {
    Observable.unsafeCreate { downstream =>
      import downstream.{scheduler => s}
      val timeoutMillis = timeout.toMillis

      source.unsafeSubscribeFn(new SyncObserver[T] with Runnable { self =>
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
                hasValue = repeat

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

  def bySelector[T,U](source: Observable[T], selector: T => Observable[U]): Observable[T] =
    source.flatMapLatest(t => selector(t).ignoreElements ++ Observable.unit(t))

  def flatten[T,U](source: Observable[T], timeout: FiniteDuration, f: T => Observable[U]): Observable[U] =
    source.flatMapLatest(t => f(t).delaySubscription(timeout))

  def flattenBySelector[T,S,U](source: Observable[T], selector: T => Observable[S], f: T => Observable[U]): Observable[U] =
    source.flatMapLatest(t => selector(t).ignoreElements ++ f(t))
}
