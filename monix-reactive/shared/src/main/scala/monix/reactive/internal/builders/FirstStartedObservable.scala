/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.atomic.{Atomic, AtomicInt}
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.observers.Subscriber
import monix.reactive.{Observable, Observer}
import scala.concurrent.{Future, Promise}

private[reactive] final class FirstStartedObservable[A](source: Observable[A]*)
  extends Observable[A] {

  override def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    import subscriber.scheduler
    val finishLine = Atomic(-1)
    var idx = 0

    // Future that will get completed with the winning index,
    // meant to trigger the cancellation of all others
    val p = Promise[Int]()
    val cancelables = new Array[Cancelable](source.length)

    for (observable <- source) {
      cancelables(idx) = createSubscription(observable, subscriber, finishLine, idx, p)
      idx += 1
    }

    // If the list of observables was empty, just emit `onComplete`
    if (idx == 0) {
      subscriber.onComplete()
      Cancelable.empty
    } else {
      val composite =
        CompositeCancelable.withPadding(cancelables.toSet, NoPadding)

      // When we have a winner, cancel the rest!
      for (idx <- p.future) {
        val c = cancelables(idx)
        val other = composite.getAndSet(Set(c))
        Cancelable.cancelAll(other - c)
      }

      composite
    }
  }

  // Helper function used for creating a subscription that uses `finishLine` as guard
  def createSubscription(observable: Observable[A], observer: Observer[A],
    finishLine: AtomicInt, idx: Int, p: Promise[Int])
    (implicit s: Scheduler): Cancelable = {

    observable.unsafeSubscribeFn(new Observer[A] {
      // for fast path
      private[this] var finishLineCache = -1

      private def shouldStream(): Boolean = {
        if (finishLineCache != idx) finishLineCache = finishLine.get
        if (finishLineCache == idx)
          true
        else if (finishLineCache >= 0 || !finishLine.compareAndSet(-1, idx))
          false
        else {
          p.success(idx)
          finishLineCache = idx
          true
        }
      }

      def onNext(elem: A): Future[Ack] = {
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
