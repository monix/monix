/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.builders

import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.{Atomic, AtomicInt}
import monifu.reactive.Ack.Cancel
import monifu.reactive.{Ack, Observable, Observer}

import scala.concurrent.Future

object amb {
  /**
   * Given a list of source Observables, emits all of the items from the first of
   * these Observables to emit an item and cancel the rest.
   */
  def apply[T](source: Observable[T]*): Observable[T] = {
    // helper function used for creating a subscription that uses `finishLine` as guard
    def createSubscription(observable: Observable[T], observer: Observer[T], finishLine: AtomicInt, idx: Int)(implicit s: Scheduler): Unit =
      observable.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = {
          if (finishLine.get == idx || finishLine.compareAndSet(0, idx))
            observer.onNext(elem)
          else
            Cancel
        }

        def onError(ex: Throwable): Unit = {
          if (finishLine.get == idx || finishLine.compareAndSet(0, idx))
            observer.onError(ex)
        }

        def onComplete(): Unit = {
          if (finishLine.get == idx || finishLine.compareAndSet(0, idx))
            observer.onComplete()
        }
      })

    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      val finishLine = Atomic(0)
      var idx = 0
      for (observable <- source) {
        createSubscription(observable, observer, finishLine, idx + 1)
        idx += 1
      }

      // if the list of observables was empty, just
      // emit `onComplete`
      if (idx == 0) observer.onComplete()
    }
  }
}
