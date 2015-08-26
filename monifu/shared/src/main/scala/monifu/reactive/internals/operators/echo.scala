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
import monifu.concurrent.locks.SpinLock
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.{Ack, Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try


private[reactive] object echo {
  /**
   * Implementation for [[Observable!.echo]].
   */
  def apply[T](source: Observable[T], timeout: FiniteDuration, onlyOnce: Boolean): Observable[T] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val downstream = subscriber.observer
      val timeoutMillis = timeout.toMillis

      source.unsafeSubscribe(new Observer[T] {
        private[this] val lock = SpinLock()
        private[this] val task = MultiAssignmentCancelable()
        private[this] var ack: Future[Ack] = Continue
        private[this] var lastEvent: T = _
        private[this] var lastTSInMillis: Long = 0L
        private[this] var hasValue = false

        private[this] val unfreeze: Ack => Ack = {
          case Continue =>
            lock.enter {
              hasValue = true
              lastTSInMillis = s.currentTimeMillis()
              Continue
            }
          case Cancel =>
            Cancel
        }

        def onNext(elem: T): Future[Ack] = lock.enter {
          lastEvent = elem
          ack = ack.onContinueStreamOnNext(downstream, elem)
            .fastFlatMap(unfreeze)
          ack
        }

        def onError(ex: Throwable): Unit =
          lock.enter {
            task.cancel()
            ack.onContinueSignalError(downstream, ex)
            ack = Cancel
          }

        def onComplete(): Unit =
          lock.enter {
            task.cancel()
            ack.onContinueSignalComplete(downstream)
            ack = Cancel
          }

        new Runnable { self =>
          private[this] val scheduleAfterContinue: Try[Ack] => Unit = {
            case Continue.IsSuccess =>
              scheduleNext(timeoutMillis)
            case _ =>
              ()
          }

          def scheduleNext(delayMillis: Long): Unit = {
            task := s.scheduleOnce(delayMillis, TimeUnit.MILLISECONDS, self)
          }

          def run(): Unit = lock.enter {
            if (!ack.isCompleted) {
              // The consumer is still processing its last message,
              // and this processing time does not enter the picture.
              // Given that the lastTSInMillis is set after Continue
              // happens, it means that we'll wait for Continue plus
              // our period in order to get another chance to emit
              ack.onComplete(scheduleAfterContinue)
            }
            else if (lastEvent == null || !hasValue) {
              // on this branch either the data source hasn't emitted anything
              // yet (lastEvent == null), or we don't have a new value since
              // the last time we've tried (!hasValue), so keep waiting
              scheduleNext(timeoutMillis)
            }
            else {
              val rightNow = s.currentTimeMillis()
              val sinceLastOnNext = rightNow - lastTSInMillis

              if (sinceLastOnNext >= timeoutMillis) {
                // hasValue is set to false only if the onlyOnce param is
                // set to true (otherwise we keep repeating our current
                // value until a new one happens)
                hasValue = !onlyOnce

                // this call is actually synchronous because we're testing
                // for ack.isCompleted above, but doing it nonetheless because
                // of safety and because last ack might have been a Cancel
                val next = ack.onContinueStreamOnNext(downstream, lastEvent)
                
                // applying back-pressure again, this time on a result
                // that might or might not be completed
                ack = next.fastFlatMap {
                  case Continue =>
                    // the speed with which the downstream replied with Continue
                    // matters in this case, so we are measuring it and 
                    // subtracting it from the period
                    val executionTime = s.currentTimeMillis() - rightNow
                    val delay = if (timeoutMillis > executionTime)
                      timeoutMillis - executionTime else 0L

                    scheduleNext(delay)
                    Continue

                  case Cancel =>
                    Cancel
                }
              }
              else {
                val remainingTime = timeoutMillis - sinceLastOnNext
                scheduleNext(remainingTime)
              }
            }
          }

          locally {
            scheduleNext(timeoutMillis)
          }
        }
      })
    }
  }
}
