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

package monix.internal.operators

import monix.concurrent.Scheduler
import monix.subjects.ReplaySubject
import monix.{Ack, Observable, Observer, Subject}
import scala.concurrent.Future


private[monix] object repeat {
  /**
   * Implementation for [[Observable.repeat]].
   */
  def elements[T](source: Observable[T]): Observable[T] = {
    // recursive function - subscribes the observer again when
    // onComplete happens
    def loop(subject: Subject[T, T], observer: Observer[T])(implicit s: Scheduler): Unit =
      subject.unsafeSubscribeFn(new Observer[T] {
        def onNext(elem: T) = {
          observer.onNext(elem)
        }

        def onError(ex: Throwable) =
          observer.onError(ex)

        def onComplete(): Unit =
          loop(subject, observer)
      })

    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}
      val subject = ReplaySubject[T]()
      loop(subject, subscriber)

      source.unsafeSubscribeFn(new Observer[T] {
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

  /** Implementation for [[monix.Observable.repeatTask]] */
  def task[T](t: => T): Observable[T] = {
    Observable.fromIterator(new TaskIterator[T](t))
  }

  private final class TaskIterator[T](t: => T) extends Iterator[T] {
    val hasNext = true
    def next(): T = t
  }
}
