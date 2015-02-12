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
import monifu.reactive.{Observable, Observer, Ack}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

object zip {
  /**
   * Implements [[Observable.zip]].
   */
  def apply[T, U](first: Observable[T], second: Observable[U])(implicit s: Scheduler) = {
    Observable.create[(T, U)] { observerOfPairs =>
      // using mutability, receiving data from 2 producers, so must synchronize
      val lock = new AnyRef
      val queueA = mutable.Queue.empty[(Promise[U], Promise[Ack])]
      val queueB = mutable.Queue.empty[(U, Promise[Ack])]
      var isCompleted = false
      var ack = Continue : Future[Ack]

      def _onError(ex: Throwable) = lock.synchronized {
        if (!isCompleted) {
          isCompleted = true
          queueA.clear()
          queueB.clear()
          observerOfPairs.onError(ex)
        }
      }

      first.unsafeSubscribe(new Observer[T] {
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

      second.unsafeSubscribe(new Observer[U] {
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

        def onError(ex: Throwable) = _onError(ex)

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
}
