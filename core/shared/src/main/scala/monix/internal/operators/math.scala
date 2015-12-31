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

import monix.Ack.{Cancel, Continue}
import monix.{Ack, Observer, Observable}
import monix.internal._
import scala.concurrent.Future
import scala.util.control.NonFatal

private[monix] object math {
  /**
   * Implementation for [[Observable.count]].
   */
  def count[T](source: Observable[T]): Observable[Long] = {
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var count = 0l

        def onNext(elem: T): Future[Ack] = {
          count += 1
          Continue
        }

        def onComplete() = {
          subscriber.onNext(count)
            .onContinueSignalComplete(subscriber)
        }

        def onError(ex: Throwable) = {
          subscriber.onError(ex)
        }
      })
    }
  }

  /**
   * Implementation for [[Observable.sum]].
   */
  def sum[T](source: Observable[T])(implicit ev: Numeric[T]): Observable[T] = {
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var result = ev.zero

        def onNext(elem: T): Future[Ack] = {
          result = ev.plus(result, elem)
          Continue
        }

        def onError(ex: Throwable) =
          subscriber.onError(ex)

        def onComplete(): Unit = {
          subscriber.onNext(result)
            .onContinueSignalComplete(subscriber)
        }
      })
    }
  }

  /**
   * Implementation for [[Observable.minBy]].
   */
  def minBy[T,U](source: Observable[T])(f: T => U)(implicit ev: Ordering[U]): Observable[T] = {
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var minValue: T = _
        private[this] var minValueU: U = _
        private[this] var hasValue = false

        def onNext(elem: T): Future[Ack] = {
          try {
            if (!hasValue) {
              hasValue = true
              minValue = elem
              minValueU = f(elem)
            }
            else {
              val m = f(elem)
              if (ev.compare(m, minValueU) < 0) {
                minValue = elem
                minValueU = m
              }
            }

            Continue
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Cancel
          }
        }

        def onError(ex: Throwable): Unit =
          subscriber.onError(ex)

        def onComplete(): Unit = {
          if (!hasValue)
            subscriber.onComplete()
          else {
            subscriber.onNext(minValue)
              .onContinueSignalComplete(subscriber)
          }
        }
      })
    }
  }

  /**
   * Implementation for [[Observable.min]].
   */
  def min[T](source: Observable[T])(implicit ev: Ordering[T]): Observable[T] = {
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var minValue: T = _
        private[this] var hasValue = false

        def onNext(elem: T): Future[Ack] = {
          if (!hasValue) {
            hasValue = true
            minValue = elem
          }
          else if (ev.compare(elem, minValue) < 0) {
            minValue = elem
          }

          Continue
        }

        def onError(ex: Throwable): Unit = subscriber.onError(ex)
        def onComplete(): Unit = {
          if (!hasValue)
            subscriber.onComplete()
          else {
            subscriber.onNext(minValue)
              .onContinueSignalComplete(subscriber)
          }
        }
      })
    }
  }

  def maxBy[T,U](source: Observable[T])(f: T => U)(implicit ev: Ordering[U]): Observable[T] = {
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var maxValue: T = _
        private[this] var maxValueU: U = _
        private[this] var hasValue = false

        def onNext(elem: T): Future[Ack] = {
          try {
            if (!hasValue) {
              hasValue = true
              maxValue = elem
              maxValueU = f(elem)
            }
            else {
              val m = f(elem)
              if (ev.compare(m, maxValueU) > 0) {
                maxValue = elem
                maxValueU = m
              }
            }

            Continue
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Cancel
          }
        }

        def onError(ex: Throwable): Unit =
          subscriber.onError(ex)

        def onComplete(): Unit = {
          if (!hasValue)
            subscriber.onComplete()
          else {
            subscriber.onNext(maxValue)
              .onContinueSignalComplete(subscriber)
          }
        }
      })
    }
  }

  /**
   * Implementation for [[Observable.max]].
   */
  def max[T](source: Observable[T])(implicit ev: Ordering[T]): Observable[T] = {
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var maxValue: T = _
        private[this] var hasValue = false

        def onNext(elem: T): Future[Ack] = {
          if (!hasValue) {
            hasValue = true
            maxValue = elem
          }
          else if (ev.compare(elem, maxValue) > 0) {
            maxValue = elem
          }
          Continue
        }

        def onError(ex: Throwable): Unit = subscriber.onError(ex)
        def onComplete(): Unit = {
          if (!hasValue)
            subscriber.onComplete()
          else {
            subscriber.onNext(maxValue)
              .onContinueSignalComplete(subscriber)
          }
        }
      })
    }
  }
}
