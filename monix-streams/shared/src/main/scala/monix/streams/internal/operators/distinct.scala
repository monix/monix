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

import monix.streams.Ack.{Cancel, Continue}
import monix.streams.{Observable, Observer}
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal


private[monix] object distinct {
  /**
    * Implementation for [[Observable.distinct]].
    */
  def distinct[T](source: Observable[T]): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] val set = mutable.Set.empty[T]

        def onNext(elem: T) = {
          if (set(elem)) Continue else {
            set += elem
            subscriber.onNext(elem)
          }
        }

        def onError(ex: Throwable) = subscriber.onError(ex)
        def onComplete() = subscriber.onComplete()
      })
    }

  def distinctBy[T, U](source: Observable[T])(fn: T => U): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] val set = mutable.Set.empty[U]

        def onNext(elem: T) = {
          var streamError = true
          try {
            val key = fn(elem)
            streamError = false

            if (set(key)) Continue
            else {
              set += key
              subscriber.onNext(elem)
            }
          }
          catch {
            case NonFatal(ex) =>
              if (!streamError) Future.failed(ex) else {
                subscriber.onError(ex)
                Cancel
              }
          }
        }

        def onError(ex: Throwable) = {
          subscriber.onError(ex)
        }

        def onComplete() = {
          subscriber.onComplete()
        }
      })
    }

  /**
    * Implementation for `Observable.distinctUntilChanged`.
    */
  def untilChanged[T](source: Observable[T]): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var isFirst = true
        private[this] var lastElem: T = _

        def onNext(elem: T) = {
          if (isFirst) {
            lastElem = elem
            isFirst = false
            subscriber.onNext(elem)
          }
          else if (lastElem != elem) {
            lastElem = elem
            subscriber.onNext(elem)
          }
          else
            Continue
        }

        def onError(ex: Throwable) = subscriber.onError(ex)
        def onComplete() = subscriber.onComplete()
      })
    }

  /**
    * Implementation for `Observable.distinctUntilChanged(fn)`.
    */
  def untilChangedBy[T, U](source: Observable[T])(fn: T => U): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
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
              subscriber.onNext(elem)
            }
            else if (lastKey != key) {
              lastKey = key
              subscriber.onNext(elem)
            }
            else
              Continue
          }
          catch {
            case NonFatal(ex) =>
              if (!streamError) Future.failed(ex) else {
                subscriber.onError(ex)
                Cancel
              }
          }
        }

        def onError(ex: Throwable) = subscriber.onError(ex)
        def onComplete() = subscriber.onComplete()
      })
    }
}
