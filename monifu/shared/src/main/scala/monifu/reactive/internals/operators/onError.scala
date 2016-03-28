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

package monifu.reactive.internals.operators

import monifu.concurrent.Scheduler
import monifu.reactive.{Subscriber, Observer, Observable}
import scala.util.control.NonFatal


private[reactive] object onError {
  /**
   * Implementation for [[Observable.onErrorRecoverWith]].
   */
  def recoverWith[T](source: Observable[T], pf: PartialFunction[Throwable, Observable[T]]) =
    Observable.create[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.onSubscribe(new Observer[T] {
        def onNext(elem: T) = subscriber.onNext(elem)
        def onComplete() = subscriber.onComplete()

        def onError(ex: Throwable) = {
          // protecting user level code
          var streamError = true
          try {
            if (pf.isDefinedAt(ex)) {
              val fallbackTo = pf(ex)
              // need asynchronous execution to avoid a synchronous loop
              // blowing out the call stack
              s.execute(fallbackTo.onSubscribe(subscriber))
            }
            else {
              // we can't protect the onError call and if it throws
              // the behavior should be undefined
              streamError = false
              subscriber.onError(ex)
            }
          }
          catch {
            case NonFatal(err) if streamError =>
              // streaming the immediate exception
              try subscriber.onError(err) finally {
                // logging the original exception
                s.reportFailure(ex)
              }
          }
        }
      })
    }

  /**
   * Implementation for [[Observable.onErrorFallbackTo]].
   */
  def fallbackTo[T](source: Observable[T], other: => Observable[T]) =
    Observable.create[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.onSubscribe(new Observer[T] {
        def onNext(elem: T) =
          subscriber.onNext(elem)

        def onError(ex: Throwable) = {
          try {
            val fallback = other
            // need asynchronous execution to avoid a synchronous loop
            // blowing out the call stack
            s.execute(fallback.onSubscribe(subscriber))
          }
          catch {
            case NonFatal(err) =>
              // streaming the immediate exception
              try subscriber.onError(err) finally {
                // logging the original exception
                s.reportFailure(ex)
              }
          }
        }

        def onComplete() =
          subscriber.onComplete()
      })
    }

  /**
   * Implementation for [[Observable.onErrorRetry]].
   */
  def retryCounted[T](source: Observable[T], maxRetries: Long) = {
    // helper to subscribe in a loop when onError happens
    def subscribe(o: Observer[T], retryIdx: Long)(implicit s: Scheduler): Unit =
      source.onSubscribe(new Observer[T] {
        def onNext(elem: T) = o.onNext(elem)
        def onComplete() = o.onComplete()

        def onError(ex: Throwable) = {
          if (retryIdx < maxRetries) {
            // need asynchronous execution to avoid a synchronous loop
            // blowing out the call stack
            s.execute(subscribe(o, retryIdx+1))
          }
          else {
            o.onError(ex)
          }
        }
      })

    Observable.create[T] { s =>
      subscribe(s, 0)(s.scheduler)
    }
  }

  /**
   * Implementation for [[Observable.onErrorRetryUnlimited]].
   */
  def retryUnlimited[T](source: Observable[T]): Observable[T] = {
    // helper to subscribe in a loop when onError happens
    def subscribe(o: Observer[T])(implicit s: Scheduler): Unit =
      source.onSubscribe(new Observer[T] {
        def onNext(elem: T) = o.onNext(elem)
        def onComplete() = o.onComplete()

        def onError(ex: Throwable) = {
          // need asynchronous execution to avoid a synchronous loop
          // blowing out the call stack
          s.execute(subscribe(o))
        }
      })

    Observable.create[T] { s =>
      subscribe(s)(s.scheduler)
    }
  }

  /**
   * Implementation for [[Observable.onErrorRetryIf]].
   */
  def retryIf[T](source: Observable[T], p: Throwable => Boolean) = {
    // helper to subscribe in a loop when onError happens
    def subscribe(o: Subscriber[T]): Unit = {
      import o.scheduler

      source.onSubscribe(new Observer[T] {
        def onNext(elem: T) = o.onNext(elem)
        def onComplete() = o.onComplete()

        def onError(ex: Throwable) = {
          // protecting against user level code
          try {
            val shouldRetry = p(ex)
            // need asynchronous execution to avoid a synchronous loop
            // blowing out the call stack
            if (shouldRetry)
              o.scheduler.execute(subscribe(o))
            else
              o.onError(ex)
          }
          catch {
            case NonFatal(err) =>
              // exception is getting lost, so try logging it
              o.scheduler.reportFailure(ex)
              // reporting user code exception, which
              // is always worse if it happens
              o.onError(err)
          }
        }
      })
    }


    Observable.create[T](subscribe)
  }
}
