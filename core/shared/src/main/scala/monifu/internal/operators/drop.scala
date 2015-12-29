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

package monifu.internal.operators

import monifu.Ack.{Cancel, Continue}
import monifu.{Ack, Observer, Observable}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

private[monifu] object drop {
  /**
   * Implementation for [[Observable.dropByTimespan]].
   */
  def byCount[T](source: Observable[T], nr: Int): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var count = 0L

        def onNext(elem: T) = {
          if (count < nr) {
            count += 1
            Continue
          }
          else
            subscriber.onNext(elem)
        }

        def onComplete() =
          subscriber.onComplete()

        def onError(ex: Throwable) =
          subscriber.onError(ex)
      })
    }

  /**
   * Implementation for `Observable.drop(timespan)`.
   */
  def byTimespan[T](source: Observable[T], timespan: FiniteDuration): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] with Runnable {
        @volatile private[this] var shouldDrop = true

        private[this] val task =
          s.scheduleOnce(timespan, this)

        def onNext(elem: T): Future[Ack] = {
          if (shouldDrop)
            Continue
          else
            subscriber.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          task.cancel()
          subscriber.onError(ex)
        }

        def onComplete(): Unit = {
          task.cancel()
          subscriber.onComplete()
        }

        def run(): Unit = {
          shouldDrop = false
        }
      })
    }

  /**
   * Implementation for [[Observable.dropWhile]].
   */
  def byPredicate[T](source: Observable[T])(p: T => Boolean): Observable[T] =
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        var continueDropping = true

        def onNext(elem: T) = {
          if (continueDropping) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isStillInvalid = p(elem)
              streamError = false

              if (isStillInvalid)
                Continue
              else {
                continueDropping = false
                subscriber.onNext(elem)
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { subscriber.onError(ex); Cancel } else Future.failed(ex)
            }
          }
          else
            subscriber.onNext(elem)
        }

        def onComplete() =
          subscriber.onComplete()

        def onError(ex: Throwable) =
          subscriber.onError(ex)
      })
    }

  /**
   * Implementation for [[Observable.dropWhileWithIndex]].
   */
  def byPredicateWithIndex[T](source: Observable[T])(p: (T, Int) => Boolean): Observable[T] =
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        var continueDropping = true
        var index = 0

        def onNext(elem: T) = {
          if (continueDropping) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isStillInvalid = p(elem, index)
              streamError = false

              if (isStillInvalid) {
                index += 1
                Continue
              }
              else {
                continueDropping = false
                subscriber.onNext(elem)
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { subscriber.onError(ex); Cancel } else Future.failed(ex)
            }
          }
          else
            subscriber.onNext(elem)
        }

        def onComplete() =
          subscriber.onComplete()

        def onError(ex: Throwable) =
          subscriber.onError(ex)
      })
    }
}
