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
import monix.streams.internal._
import monix.streams.{Ack, Observable, Observer}
import scala.collection.mutable
import scala.concurrent.{Future, Promise}

private[monix] object zip {
  /**
    * Implements [[Observable.zip]].
    */
  def two[T, U](first: Observable[T], second: Observable[U]) = {
    Observable.unsafeCreate[(T, U)] { observerOfPairs =>
      import observerOfPairs.{scheduler => s}

      // using mutability, receiving data from 2 producers, so must synchronize
      val lock = new AnyRef
      val queueA = mutable.Queue.empty[(Promise[U], Promise[Ack])]
      val queueB = mutable.Queue.empty[(U, Promise[Ack])]
      var isCompleted = false
      var ack = Continue : Future[Ack]

      def _onError(ex: Throwable) =
        ack.onContinue {
          lock.synchronized {
            if (!isCompleted) {
              isCompleted = true
              queueA.clear()
              queueB.clear()
              observerOfPairs.onError(ex)
            }
          }
        }

      first.unsafeSubscribeFn(new Observer[T] {
        def onNext(a: T): Future[Ack] =
          lock.synchronized {
            if (isCompleted)
              Cancel
            else if (queueB.isEmpty) {
              val resp = Promise[Ack]()
              val promiseForB = Promise[U]()
              queueA.enqueue((promiseForB, resp))

              ack = promiseForB.future.flatMap(b => observerOfPairs.onNext((a, b)))
              resp.completeWith(ack)
              ack
            }
            else {
              val (b, bResponse) = queueB.dequeue()
              val f = observerOfPairs.onNext((a, b))
              bResponse.completeWith(f)
              f
            }
          }

        def onError(ex: Throwable) =
          _onError(ex)

        def onComplete() =
          ack.onContinue {
            lock.synchronized {
              if (!isCompleted && queueA.isEmpty) {
                isCompleted = true
                queueA.clear()
                queueB.clear()
                observerOfPairs.onComplete()
              }
            }
          }
      })

      second.unsafeSubscribeFn(new Observer[U] {
        def onNext(b: U): Future[Ack] =
          lock.synchronized {
            if (isCompleted)
              Cancel
            else if (queueA.nonEmpty) {
              val (bPromise, response) = queueA.dequeue()
              bPromise.success(b)
              response.future
            }
            else {
              val p = Promise[Ack]()
              queueB.enqueue((b, p))
              p.future
            }
          }

        def onError(ex: Throwable) =
          _onError(ex)

        def onComplete() =
          ack.onContinue {
            lock.synchronized {
              if (!isCompleted && queueB.isEmpty) {
                isCompleted = true
                queueA.clear()
                queueB.clear()
                observerOfPairs.onComplete()
              }
            }
          }
      })
    }
  }

  /** Implementation for [[Observable.zipWithIndex]] */
  def withIndex[T](source: Observable[T]): Observable[(T, Long)] =
    Observable.unsafeCreate { downstream =>
      import downstream.scheduler

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var index = 0

        def onNext(elem: T): Future[Ack] = {
          val oldIndex = index
          index += 1
          downstream.onNext(elem -> oldIndex)
        }

        def onError(ex: Throwable): Unit =
          downstream.onError(ex)

        def onComplete(): Unit =
          downstream.onComplete()
      })
    }
}
