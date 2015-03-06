/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.{SynchronousObserver, WhileBusyBufferSubscriber}
import monifu.reactive.{Ack, Observable, Observer}
import scala.concurrent.Future
import scala.util.{Failure, Success}

object whileBusy {
  /**
   * While the destination observer is busy, drop the incoming events.
   */
  def dropEvents[T](source: Observable[T]): Observable[T] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new SynchronousObserver[T] {
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
                  observer.onError(ex)
                  Cancel

                case Success(Continue) =>
                  observer.onNext(elem) match {
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
            observer.onError(ex)
          }

        def onComplete() =
          if (!isDone) {
            isDone = true
            observer.onComplete()
          }
      })
    }

  /**
   * Implementation for [[Observable.whileBusyBufferEvents]].
   */
  def bufferEvents[T](source: Observable[T], bufferSize: Int): Observable[Seq[T]] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val buffer = WhileBusyBufferSubscriber(subscriber.observer, bufferSize)
      val downstream = buffer.observer

      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = {
          downstream.onNext(elem)
        }

        def onError(ex: Throwable) = {
          downstream.onError(ex)
        }

        def onComplete() = {
          downstream.onComplete()
        }
      })
    }
}
