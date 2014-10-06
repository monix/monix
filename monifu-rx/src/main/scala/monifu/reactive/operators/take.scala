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
import monifu.concurrent.atomic.AtomicBoolean
import monifu.concurrent.locks.SpinLock
import monifu.reactive.Ack.{Continue, Cancel}
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.{Ack, Observer, Observable}
import monifu.reactive.internals._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


object take {
  /**
   * Implementation for [[monifu.reactive.Observable.take]].
   */
  def left[T](source: Observable[T], n: Int): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var counter = 0
        private[this] var isDone = false

        def onNext(elem: T) = {
          if (n <= 0 && !isDone) {
            isDone = true
            observer.onComplete()
            Cancel
          }
          else if (!isDone && counter < n) {
            // ^^ short-circuit for not endlessly incrementing that number
            counter += 1

            if (counter < n) {
              // this is not the last event in the stream, so send it directly
              observer.onNext(elem)
            }
            else  {
              // last event in the stream, so we need to send the event followed by an EOF downstream
              // after which we signal upstream to the producer that it should stop
              isDone = true
              observer.onNext(elem)
              observer.onComplete()
              Cancel
            }
          }
          else {
            // we already emitted the maximum number of events, so signal upstream
            // to the producer that it should stop sending events
            Cancel
          }
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
   * Implementation for [[monifu.reactive.Observable.takeRight]].
   */
  def right[T](source: Observable[T], n: Int)(implicit s: Scheduler) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] val queue = mutable.Queue.empty[T]
        private[this] var queued = 0

        def onNext(elem: T): Future[Ack] = {
          if (n <= 0)
            Cancel
          else if (queued < n) {
            queue.enqueue(elem)
            queued += 1
          }
          else {
            queue.enqueue(elem)
            queue.dequeue()
          }
          Continue
        }

        def onError(ex: Throwable): Unit = {
          queue.clear()
          observer.onError(ex)
        }

        def onComplete(): Unit = {
          Observable.from(queue).unsafeSubscribe(observer)
        }
      })
    }

  def leftByTimespan[T](source: Observable[T], timespan: FiniteDuration)(implicit s: Scheduler) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] val lock = SpinLock()
        private[this] var isDone = false

        private[this] val task =
          s.scheduleOnce(timespan, onComplete())

        def onNext(elem: T): Future[Ack] =
          lock.enter {
            if (!isDone)
              observer.onNext(elem).onCancel {
                lock.enter {
                  task.cancel()
                  isDone = true
                }
              }
            else
              Cancel
          }

        def onError(ex: Throwable): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              observer.onError(ex)
            }
          }

        def onComplete(): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              observer.onComplete()
            }
          }
      })
    }

  /**
   * Implementation for [[Observable.takeUntilOtherEmits]].
   */
  def untilOtherEmits[T, U](source: Observable[T], other: Observable[U])(implicit s: Scheduler) = {
    Observable.create[T] { observer =>
      // we've got contention between the source and the other observable
      // and we can't use an Atomic here because of `onNext`, so we are
      // forced to use a lock in order to preserve the non-concurrency
      // clause in the contract
      val lock = SpinLock()
      // must be at all times synchronized by lock
      var isDone = false

      def terminate(ex: Throwable = null): Unit =
        lock.enter {
          if (!isDone) {
            isDone = true
            if (ex == null)
              observer.onComplete()
            else
              observer.onError(ex)
          }
        }

      source.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T) = lock.enter {
          if (isDone) Cancel
          else
            observer.onNext(elem).onCancel {
              lock.enter { isDone = true }
            }
        }

        def onError(ex: Throwable): Unit = terminate(ex)
        def onComplete(): Unit = terminate()
      })

      other.unsafeSubscribe(new SynchronousObserver[U] {
        def onNext(elem: U) = {
          terminate()
          Cancel
        }

        def onError(ex: Throwable) = terminate(ex)
        def onComplete() = terminate()
      })
    }
  }

  /**
   * Implementation for [[monifu.reactive.Observable.takeWhile]].
   */
  def byPredicate[T](source: Observable[T])(p: T => Boolean) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            // See Section 6.4. in the Rx Design Guidelines:
            // Protect calls to user code from within an operator
            var streamError = true
            try {
              val isValid = p(elem)
              streamError = false
              if (isValid) {
                observer.onNext(elem)
              }
              else {
                shouldContinue = false
                observer.onComplete()
                Cancel
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) {
                  observer.onError(ex)
                  Cancel
                }
                else
                  Future.failed(ex)
            }
          }
          else
            Cancel
        }

        def onComplete() = {
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }

  /**
   * Implementation for [[monifu.reactive.Observable.takeWhileRefIsTrue]].
   */
  def whileRefIsTrue[T](source: Observable[T], ref: AtomicBoolean) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            if (ref.get)
              observer.onNext(elem)
            else {
              shouldContinue = false
              observer.onComplete()
              Cancel
            }
          }
          else
            Cancel
        }

        def onComplete() = {
          observer.onComplete()
        }

        def onError(ex: Throwable) = {
          observer.onError(ex)
        }
      })
    }
}
