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
 *
 */

package monix.streams.internal.operators

import java.util.concurrent.TimeUnit
import monix.streams.Ack.Cancel
import monix.streams.internal._
import monix.streams.{Ack, Observable, Observer}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

private[monix] object delay {
  /**
    * Implementation for [[Observable.delay]].
    */
  def bySelector[T,U](source: Observable[T], selector: T => Observable[U]) =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.scheduler

      source.unsafeSubscribeFn(new Observer[T] { self =>
        private[this] var currentElem: T = _
        private[this] var ack: Promise[Ack] = null

        val delayingElement = new Observer[U] {
          def onNext(elem: U): Future[Ack] = {
            onComplete()
            Cancel
          }

          def onError(ex: Throwable): Unit = {
            subscriber.onError(ex)
            ack.failure(ex)
          }

          def onComplete(): Unit = {
            subscriber.onNext(currentElem)
              .onCompleteNow { r => ack.complete(r) }
          }
        }

        def onNext(elem: T): Future[Ack] = {
          currentElem = elem
          ack = Promise()

          var streamErrors = true
          try {
            val obs = selector(elem)
            streamErrors = false
            obs.unsafeSubscribeFn(delayingElement)
            ack.future
          }
          catch {
            case NonFatal(ex) =>
              if (streamErrors) { onError(ex); Cancel }
              else Future.failed(ex)
          }
        }

        def onError(ex: Throwable): Unit = {
          subscriber.onError(ex)
        }

        def onComplete(): Unit = {
          subscriber.onComplete()
        }
      })
    }

  /**
   * Implementation for [[Observable.delay]].
   */
  def byDuration[T](source: Observable[T], delay: FiniteDuration) =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.scheduler

      source.unsafeSubscribeFn(new Observer[T] with Runnable { self =>
        private[this] val delayMs = delay.toMillis
        private[this] var currentElem: T = _
        private[this] var ack: Promise[Ack] = null

        def onNext(elem: T): Future[Ack] = {
          currentElem = elem
          ack = Promise()
          scheduler.scheduleOnce(delayMs, TimeUnit.MILLISECONDS, self)
          ack.future
        }

        def onError(ex: Throwable): Unit = {
          subscriber.onError(ex)
        }

        def onComplete(): Unit = {
          subscriber.onComplete()
        }

        def run(): Unit = {
          subscriber.onNext(currentElem)
            .onCompleteNow { r => ack.complete(r) }
        }
      })
    }
}
