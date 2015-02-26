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

import monifu.concurrent.Scheduler
import monifu.reactive.subjects.ReplaySubject
import monifu.reactive.{Ack, Observable, Observer, Subject}
import scala.concurrent.Future


object repeat {
  /**
   * Implementation for [[Observable.repeat]].
   */
  def apply[T](source: Observable[T]): Observable[T] = {
    // recursive function - subscribes the observer again when
    // onComplete happens
    def loop(subject: Subject[T, T], observer: Observer[T])(implicit s: Scheduler): Unit =
      subject.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = {
          observer.onNext(elem)
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onComplete(): Unit =
          loop(subject, observer)
      })

    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      val subject = ReplaySubject[T]()
      loop(subject, observer)

      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          subject.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          subject.onError(ex)
        }

        def onComplete(): Unit = {
          subject.onComplete()
        }
      })
    }
  }
}
