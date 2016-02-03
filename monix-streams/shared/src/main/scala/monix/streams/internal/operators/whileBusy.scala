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

import monix.streams.observers.SyncObserver
import monix.streams.{Observable, Ack}
import monix.streams.Ack.{Cancel, Continue}
import monix.streams.internal._
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

private[monix] object whileBusy {
  /**
   * While the destination subscriber is busy, drop the incoming events.
   */
  def dropEvents[T](source: Observable[T]): Observable[T] =
    Observable.unsafeCreate { subscriber =>
      implicit val s = subscriber.scheduler

      source.unsafeSubscribeFn(new SyncObserver[T] {
        private[this] var lastAck = Continue : Future[Ack]
        private[this] var isDone = false

        def onNext(elem: T) = {
          if (!isDone) lastAck match {
            case sync if sync.isCompleted =>
              sync.value.get match {
                case Success(Cancel) =>
                  isDone = true
                  Cancel

                case Failure(ex) =>
                  isDone = true
                  subscriber.onError(ex)
                  Cancel

                case Success(Continue) =>
                  subscriber.onNext(elem) match {
                    case Cancel =>
                      isDone = true
                      Cancel
                    case other =>
                      lastAck = other
                      Continue
                  }
              }

            case _ =>
              Continue
          }
          else
            Cancel
        }

        def onError(ex: Throwable) =
          if (!isDone) {
            isDone = true
            lastAck.onContinueSignalError(subscriber, ex)
          }

        def onComplete() =
          if (!isDone) {
            isDone = true
            lastAck.onContinueSignalComplete(subscriber)
          }
      })
    }

  /**
   * While the destination subscriber is busy,
   * drop the incoming events, then signal how many events
   * where dropped.
   */
  def dropEventsThenSignalOverflow[T](source: Observable[T], onOverflow: Long => T): Observable[T] =
    Observable.unsafeCreate { subscriber =>
      implicit val s = subscriber.scheduler

      source.unsafeSubscribeFn(new SyncObserver[T] {
        private[this] var lastAck = Continue : Future[Ack]
        private[this] var eventsDropped = 0L
        private[this] var isDone = false

        def onNext(elem: T) = {
          if (!isDone) lastAck match {
            case sync if sync.isCompleted =>
              sync.value.get match {
                case Success(Cancel) =>
                  isDone = true
                  Cancel

                case Failure(ex) =>
                  isDone = true
                  subscriber.onError(ex)
                  Cancel

                case Success(Continue) =>
                  val hasOverflow = eventsDropped > 0
                  var streamError = true

                  lastAck = if (hasOverflow)
                    try {
                      val message = onOverflow(eventsDropped)
                      eventsDropped = 0
                      streamError = false
                      subscriber.onNext(message)
                    }
                    catch {
                      case NonFatal(ex) if streamError =>
                        onError(ex)
                        Cancel
                    }
                  else {
                    subscriber.onNext(elem)
                  }

                  if (hasOverflow)
                    onNext(elem) // retry
                  else
                    Continue
              }

            case _ =>
              eventsDropped += 1
              Continue
          }
          else
            Cancel
        }

        def onError(ex: Throwable): Unit = if (!isDone) {
          isDone = true
          lastAck.onContinueSignalError(subscriber, ex)
        }

        def onComplete(): Unit = if (!isDone) {
          isDone = true

          val f = if (eventsDropped <= 0) lastAck else {
            var streamError = true
            try {
              val message = onOverflow(eventsDropped)
              eventsDropped = 0
              streamError = false

              lastAck.fastFlatMap {
                case Continue => subscriber.onNext(message)
                case Cancel => Cancel
              }
            }
            catch {
              case NonFatal(ex) =>
                Future.failed(ex)
            }
          }

          f.onContinueSignalComplete(subscriber)
        }
      })
    }
}
