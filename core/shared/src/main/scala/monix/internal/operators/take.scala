/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

import monix.concurrent.cancelables.BooleanCancelable
import monix.Ack.{Cancel, Continue}
import monix.internal._
import monix.{Ack, Observable, Observer}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


private[monix] object take {
  /**
   * Implementation for [[monix.Observable.take]].
   */
  def left[T](source: Observable[T], n: Long): Observable[T] =
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var counter = 0L
        private[this] var isDone = false

        def onNext(elem: T) = {
          if (n <= 0 && !isDone) {
            isDone = true
            subscriber.onComplete()
            Cancel
          }
          else if (!isDone && counter < n) {
            // ^^ short-circuit for not endlessly incrementing that number
            counter += 1

            if (counter < n) {
              // this is not the last event in the stream, so send it directly
              subscriber.onNext(elem)
            }
            else  {
              // last event in the stream, so we need to send the event followed by an EOF downstream
              // after which we signal upstream to the producer that it should stop
              isDone = true
              subscriber.onNext(elem)
                .onContinueSignalComplete(subscriber)
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
            subscriber.onError(ex)
          }

        def onComplete() =
          if (!isDone) {
            isDone = true
            subscriber.onComplete()
          }
      })
    }

  /**
   * Implementation for [[monix.Observable.takeRight]].
   */
  def right[T](source: Observable[T], n: Int): Observable[T] =
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] val queue = mutable.Queue.empty[T]
        private[this] var queued = 0

        def onNext(elem: T): Future[Ack] = {
          if (n <= 0)
            Cancel
          else if (queued < n) {
            queue.enqueue(elem)
            queued += 1
            Continue
          }
          else {
            queue.enqueue(elem)
            queue.dequeue()
            Continue
          }
        }

        def onComplete(): Unit = {
          Observable.fromIterable(queue).unsafeSubscribeFn(subscriber)
        }

        def onError(ex: Throwable): Unit = {
          Observable.fromIterable(queue).unsafeSubscribeFn(new Observer[T] {
            def onError(ex: Throwable) =
              subscriber.onError(ex)

            def onComplete() =
              subscriber.onError(ex)

            def onNext(elem: T) =
              subscriber.onNext(elem)
          })
        }
      })
    }

  def leftByTimespan[T](source: Observable[T], timespan: FiniteDuration): Observable[T] =
    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] with Runnable {
        private[this] var isActive = true
        private[this] val task = s.scheduleOnce(timespan.length, timespan.unit, this)

        def onNext(elem: T): Future[Ack] = synchronized {
          if (isActive)
            subscriber.onNext(elem)
              .ifCanceledDoCancel(task)
          else {
            onComplete()
            Cancel
          }
        }

        def onError(ex: Throwable): Unit = synchronized {
          if (isActive) {
            isActive = false
            task.cancel()
            subscriber.onError(ex)
          }
        }

        def onComplete(): Unit = synchronized {
          if (isActive) {
            isActive = false
            task.cancel()
            subscriber.onComplete()
          }
        }

        def run(): Unit = {
          onComplete()
        }
      })
    }

  /**
   * Implementation for [[monix.Observable.takeWhile]].
   */
  def byPredicate[T](source: Observable[T])(p: T => Boolean): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            // Protects calls to user code from within an operator
            var streamError = true
            try {
              val isValid = p(elem)
              streamError = false

              if (isValid) {
                subscriber.onNext(elem)
              }
              else {
                shouldContinue = false
                subscriber.onComplete()
                Cancel
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) {
                  subscriber.onError(ex)
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
          subscriber.onComplete()
        }

        def onError(ex: Throwable) = {
          subscriber.onError(ex)
        }
      })
    }

  /**
   * Implementation for [[monix.Observable.takeWhileNotCanceled]].
   */
  def takeWhileNotCanceled[T](source: Observable[T], c: BooleanCancelable): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            var streamError = true
            try {
              val isCanceled = c.isCanceled
              streamError = false

              if (!isCanceled)
                subscriber.onNext(elem)
              else {
                shouldContinue = false
                subscriber.onComplete()
                Cancel
              }
            }
            catch {
              case NonFatal(ex) =>
                if (streamError) {
                  subscriber.onError(ex)
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
          subscriber.onComplete()
        }

        def onError(ex: Throwable) = {
          subscriber.onError(ex)
        }
      })
    }
}
