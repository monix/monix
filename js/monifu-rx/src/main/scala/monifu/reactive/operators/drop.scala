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
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object drop {
  /**
   * Implementation for [[Observable.dropByTimespan]].
   */
  def byCount[T](source: Observable[T], nr: Int) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var count = 0L

        def onNext(elem: T) = {
          if (count < nr) {
            count += 1
            Continue
          }
          else
            observer.onNext(elem)
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Implementation for `Observable.drop(timespan)`.
   */
  def byTimespan[T](source: Observable[T], timespan: FiniteDuration)(implicit s: Scheduler) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        @volatile private[this] var shouldDrop = true

        private[this] val task =
          s.scheduleOnce(timespan, {
            shouldDrop = false
          })

        def onNext(elem: T): Future[Ack] = {
          if (shouldDrop)
            Continue
          else
            observer.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          task.cancel()
          observer.onError(ex)
        }

        def onComplete(): Unit = {
          task.cancel()
          observer.onComplete()
        }
      })
    }

  /**
   * Implementation for [[Observable.dropWhile]].
   */
  def byPredicate[T](source: Observable[T])(p: T => Boolean): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
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
                observer.onNext(elem)
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
            }
          }
          else
            observer.onNext(elem)
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }

  /**
   * Implementation for [[Observable.dropWhileWithIndex]].
   */
  def byPredicateWithIndex[T](source: Observable[T])(p: (T, Int) => Boolean): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
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
                observer.onNext(elem)
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) { observer.onError(ex); Cancel } else Future.failed(ex)
            }
          }
          else
            observer.onNext(elem)
        }

        def onComplete() =
          observer.onComplete()

        def onError(ex: Throwable) =
          observer.onError(ex)
      })
    }
}
