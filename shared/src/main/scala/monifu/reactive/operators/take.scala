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

package monifu.reactive.operators

import monifu.concurrent.cancelables.BooleanCancelable
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.{Ack, Observable, Observer}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


private[reactive] object take {
  /**
   * Implementation for [[monifu.reactive.Observable.take]].
   */
  def left[T](source: Observable[T], n: Long): Observable[T] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] var counter = 0L
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
                .onContinueSignalComplete(observer)
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
  def right[T](source: Observable[T], n: Int): Observable[T] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

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

        def onComplete(): Unit = {
          Observable.fromIterable(queue).unsafeSubscribe(observer)
        }

        def onError(ex: Throwable): Unit = {
          Observable.fromIterable(queue).unsafeSubscribe(new Observer[T] {
            def onError(ex: Throwable) =
              observer.onError(ex)

            def onComplete() =
              observer.onError(ex)

            def onNext(elem: T) =
              observer.onNext(elem)
          })
        }
      })
    }

  def leftByTimespan[T](source: Observable[T], timespan: FiniteDuration): Observable[T] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] with Runnable {
        private[this] var isActive = true
        private[this] val task = s.scheduleOnce(timespan, this)

        def onNext(elem: T): Future[Ack] = synchronized {
          if (isActive)
            observer.onNext(elem)
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
            observer.onError(ex)
          }
        }

        def onComplete(): Unit = synchronized {
          if (isActive) {
            isActive = false
            task.cancel()
            observer.onComplete()
          }
        }

        def run(): Unit = {
          onComplete()
        }
      })
    }

  /**
   * Implementation for [[monifu.reactive.Observable.takeWhile]].
   */
  def byPredicate[T](source: Observable[T])(p: T => Boolean): Observable[T] =
    Observable.create[T] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            // Protects calls to user code from within an operator
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
   * Implementation for [[monifu.reactive.Observable.takeWhileNotCanceled]].
   */
  def takeWhileNotCanceled[T](source: Observable[T], c: BooleanCancelable): Observable[T] =
    Observable.create[T] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        var shouldContinue = true

        def onNext(elem: T) = {
          if (shouldContinue) {
            var streamError = true
            try {
              val isCanceled = c.isCanceled
              streamError = false

              if (!isCanceled)
                observer.onNext(elem)
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
}
