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

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observer, Observable}
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal


private[reactive] object distinct {
  /**
   * Implementation for [[Observable.distinct]].
   */
  def distinct[T](source: Observable[T]): Observable[T] =
    Observable.create[T] { subscriber=>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] val set = mutable.Set.empty[T]

        def onNext(elem: T) = {
          if (set(elem)) Continue else {
            set += elem
            observer.onNext(elem)
          }
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  def distinctBy[T, U](source: Observable[T])(fn: T => U): Observable[T] =
    Observable.create[T] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] val set = mutable.Set.empty[U]

        def onNext(elem: T) = {
          var streamError = true
          try {
            val key = fn(elem)
            streamError = false

            if (set(key)) Continue
            else {
              set += key
              observer.onNext(elem)
            }
          }
          catch {
            case NonFatal(ex) =>
              if (!streamError) Future.failed(ex) else {
                observer.onError(ex)
                Cancel
              }
          }
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }

        def onComplete() = {
          observer.onComplete()
        }
      })
    }

  /**
   * Implementation for `Observable.distinctUntilChanged`.
   */
  def untilChanged[T](source: Observable[T]): Observable[T] =
    Observable.create[T] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] var isFirst = true
        private[this] var lastElem: T = _

        def onNext(elem: T) = {
          if (isFirst) {
            lastElem = elem
            isFirst = false
            observer.onNext(elem)
          }
          else if (lastElem != elem) {
            lastElem = elem
            observer.onNext(elem)
          }
          else
            Continue
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }

  /**
   * Implementation for `Observable.distinctUntilChanged(fn)`.
   */
  def untilChangedBy[T, U](source: Observable[T])(fn: T => U): Observable[T] =
    Observable.create[T] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] var isFirst = true
        private[this] var lastKey: U = _

        def onNext(elem: T) = {
          var streamError = true
          try {
            val key = fn(elem)
            streamError = false

            if (isFirst) {
              lastKey = fn(elem)
              isFirst = false
              observer.onNext(elem)
            }
            else if (lastKey != key) {
              lastKey = key
              observer.onNext(elem)
            }
            else
              Continue
          }
          catch {
            case NonFatal(ex) =>
              if (!streamError) Future.failed(ex) else {
                observer.onError(ex)
                Cancel
              }
          }
        }

        def onError(ex: Throwable) = observer.onError(ex)
        def onComplete() = observer.onComplete()
      })
    }
}
