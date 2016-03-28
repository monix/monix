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

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Observer, Observable}
import scala.concurrent.Future
import monifu.reactive.internals._

private[reactive] object misc {
  /**
   * Implements [[Observable.ignoreElements]].
   */
  def complete[T](source: Observable[T]): Observable[Nothing] =
    Observable.create { subscriber =>
      import subscriber.{scheduler => s}

      source.onSubscribe(new Observer[T] {
        def onNext(elem: T) = Continue
        def onError(ex: Throwable): Unit =
          subscriber.onError(ex)
        def onComplete(): Unit =
          subscriber.onComplete()
      })
    }

  /**
   * Implements [[Observable.error]].
   */
  def error[T](source: Observable[T]): Observable[Throwable] =
    Observable.create { subscriber =>
      import subscriber.{scheduler => s}

      source.onSubscribe(new Observer[T] {
        def onNext(elem: T) =
          Continue

        def onComplete(): Unit =
          subscriber.onComplete()

        def onError(ex: Throwable): Unit = {
          subscriber.onNext(ex)
            .onContinueSignalComplete(subscriber)
        }
      })
    }

  /**
   * Implementation for [[monifu.reactive.Observable.defaultIfEmpty]].
   */
  def defaultIfEmpty[T](source: Observable[T], default: T): Observable[T] =
    Observable.create { subscriber =>
      import subscriber.{scheduler => s}

      source.onSubscribe(new Observer[T] {
        private[this] var isEmpty = true

        def onNext(elem: T): Future[Ack] = {
          if (isEmpty) isEmpty = false
          subscriber.onNext(elem)
        }

        def onError(ex: Throwable): Unit = {
          subscriber.onError(ex)
        }

        def onComplete(): Unit = {
          if (isEmpty)
            subscriber.onNext(default)
              .onContinueSignalComplete(subscriber)
          else
            subscriber.onComplete()
        }
      })
    }

  /**
   * Implements [[Observable.endWithError]].
   */
  def endWithError[T](source: Observable[T])(error: Throwable): Observable[T] =
    Observable.create { subscriber =>
      import subscriber.{scheduler => s}

      source.onSubscribe(new Observer[T] {
        def onNext(elem: T) = subscriber.onNext(elem)
        def onError(ex: Throwable) = subscriber.onError(ex)
        def onComplete() = subscriber.onError(error)
      })
    }

  /**
   * Implements [[Observable.isEmpty]].
   */
  def isEmpty[T](source: Observable[T]): Observable[Boolean] =
    Observable.create[Boolean] { subscriber =>
      import subscriber.{scheduler => s}

      source.onSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          subscriber.onNext(false).onContinueSignalComplete(subscriber)
          Cancel
        }

        def onError(ex: Throwable): Unit =
          subscriber.onError(ex)

        def onComplete(): Unit = {
          // if we get here, it means that `onNext` never happened
          subscriber.onNext(true).onContinueSignalComplete(subscriber)
        }
      })
    }
}
