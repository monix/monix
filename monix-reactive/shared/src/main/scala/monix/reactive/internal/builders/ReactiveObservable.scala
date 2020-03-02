/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import monix.execution.Cancelable
import monix.execution.rstreams.SingleAssignSubscription
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.reactivestreams
import org.reactivestreams.{Subscription, Publisher => RPublisher}

/** Implementation for `Observable.fromReactivePublisher` */
private[reactive] final class ReactiveObservable[A](publisher: RPublisher[A], requestCount: Int) extends Observable[A] {

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    val subscription = SingleAssignSubscription()
    val sub =
      if (requestCount > 0) subscriber.toReactive(requestCount)
      else subscriber.toReactive

    publisher.subscribe(new reactivestreams.Subscriber[A] {
      def onNext(t: A): Unit = sub.onNext(t)
      def onComplete(): Unit = sub.onComplete()
      def onError(t: Throwable): Unit = sub.onError(t)

      def onSubscribe(s: Subscription): Unit = {
        subscription := s
        sub.onSubscribe(s)
      }
    })

    subscription
  }
}
