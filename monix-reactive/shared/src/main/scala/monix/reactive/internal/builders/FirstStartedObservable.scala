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

package monix.reactive.internal.builders

import monix.execution.Ack.Stop
import monix.execution.cancelables.{CompositeCancelable, MultiAssignmentCancelable}
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.observers.Subscriber
import monix.reactive.{Observable, Observer}
import monix.execution.atomic.{Atomic, AtomicInt}
import scala.concurrent.{Future, Promise}

private[reactive] final class FirstStartedObservable[T](source: Observable[T]*)
  extends Observable[T] {

  override def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
    import subscriber.scheduler
    val finishLine = Atomic(0)
    var idx = 0

    val cancelables = new Array[Cancelable](source.length)
    val p = Promise[Int]()

    for (observable <- source) {
      cancelables(idx) = createSubscription(observable, subscriber, finishLine, idx + 1, p)
      idx += 1
    }

    // if the list of observables was empty, just
    // emit `onComplete`
    if (idx == 0) {
      subscriber.onComplete()
      Cancelable.empty
    } else {
      val composite = CompositeCancelable(cancelables:_*)
      val cancelable = MultiAssignmentCancelable(composite)

      for (idx <- p.future) {
        val c = cancelables(idx)
        composite -= c
        cancelable := c
        composite.cancel()
      }

      cancelable
    }
  }

  // Helper function used for creating a subscription that uses `finishLine` as guard
  def createSubscription(observable: Observable[T], observer: Observer[T],
    finishLine: AtomicInt, idx: Int, p: Promise[Int])
    (implicit s: Scheduler): Cancelable = {

    observable.unsafeSubscribeFn(new Observer[T] {
      // for fast path
      private[this] var finishLineCache = 0

      private def shouldStream(): Boolean = {
        if (finishLineCache != idx) finishLineCache = finishLine.get
        if (finishLineCache == idx)
          true
        else if (!finishLine.compareAndSet(0, idx))
          false
        else {
          p.success(idx)
          finishLineCache = idx
          true
        }
      }

      def onNext(elem: T): Future[Ack] = {
        if (shouldStream())
          observer.onNext(elem)
        else
          Stop
      }

      def onError(ex: Throwable): Unit =
        if (shouldStream()) observer.onError(ex)
      def onComplete(): Unit =
        if (shouldStream()) observer.onComplete()
    })
  }
}
