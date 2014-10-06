/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.concurrent.locks.SpinLock
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.{Ack, Observer, Observable}
import monifu.reactive.internals._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

/**
 * Implementation for [[Observable.bufferTimed]].
 */
object buffer {
  def withSize[T](source: Observable[T], count: Int)(implicit s: Scheduler): Observable[Seq[T]] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var buffer = ArrayBuffer.empty[T]
        private[this] var lastAck = Continue : Future[Ack]
        private[this] var size = 0

        def onNext(elem: T): Future[Ack] = {
          size += 1
          buffer.append(elem)
          if (size >= count) {
            val oldBuffer = buffer
            buffer = ArrayBuffer.empty[T]
            size = 0

            lastAck = observer.onNext(oldBuffer)
            lastAck
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          observer.onError(ex)
          buffer = null
        }

        def onComplete(): Unit = {
          if (size > 0) {
            // if we don't do this, then it breaks the
            // back-pressure contract
            lastAck.onContinueCompleteWith(observer, buffer)
          }
          else
            observer.onComplete()

          buffer = null
        }
      })
    }

  def withTime[T](source: Observable[T], timespan: FiniteDuration)(implicit s: Scheduler) =
    Observable.create[Seq[T]] { observer =>
      source.unsafeSubscribe(new SynchronousObserver[T] {
        private[this] val lock = SpinLock()
        private[this] var buffer = ArrayBuffer.empty[T]
        private[this] var isDone = false
        private[this] var lastAck = Continue : Future[Ack]

        private[this] val task =
          s.scheduleRecursive(timespan, timespan, { reschedule =>
            lock.enter {
              if (!isDone) {
                val current = buffer
                buffer = ArrayBuffer.empty
                lastAck =
                  try observer.onNext(current) catch {
                    case NonFatal(ex) =>
                      Future.failed(ex)
                  }

                lastAck match {
                  case sync if sync.isCompleted =>
                    sync.value.get match {
                      case Success(Continue) =>
                        reschedule()
                      case Success(Cancel) =>
                        isDone = true
                      case Failure(ex) =>
                        isDone = true
                        observer.onError(ex)
                    }

                  case async =>
                    async.onComplete {
                      case Success(Continue) =>
                        lock.enter {
                          if (!isDone) reschedule
                        }
                      case Success(Cancel) =>
                        lock.enter {
                          isDone = true
                        }
                      case Failure(ex) =>
                        lock.enter {
                          isDone = true
                          observer.onError(ex)
                        }
                    }
                }
              }
            }
          })

        def onNext(elem: T): Ack = lock.enter {
          if (!isDone) {
            buffer.append(elem)
            Continue
          }
          else
            Cancel
        }

        def onError(ex: Throwable): Unit = lock.enter {
          if (!isDone) {
            isDone = true
            buffer = null
            observer.onError(ex)
            task.cancel()
          }
        }

        def onComplete(): Unit = lock.enter {
          if (!isDone) {
            if (buffer.nonEmpty) {
              // if we don't do this, then it breaks the
              // back-pressure contract
              lastAck.onContinueCompleteWith(observer, buffer)
            }
            else
              observer.onComplete()

            isDone = true
            buffer = null
            task.cancel()
          }
        }
      })
    }
}
