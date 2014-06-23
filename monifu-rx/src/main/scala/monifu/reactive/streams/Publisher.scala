/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
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

package monifu.reactive.streams

import monifu.reactive.observers.SafeObserver
import monifu.reactive.{Observable, Observer}

/**
 * Mirrors the `Publisher` interface from the
 * [[http://www.reactive-streams.org/ Reactive Streams]] project.
 */
trait Publisher[T] extends Any {
  /**
   * Request the publisher to start emitting data.
   *
   * This is a factory method and can be called multiple times, each time
   * starting a new [[Subscription]]. Each [[Subscription]] will work
   * for only a single [[Subscriber]]. A [[Subscriber]] should only
   * subscribe once to a single [[Publisher]].
   *
   * If the [[Publisher]] rejects the subscription attempt or otherwise fails
   * it will signal the error via [[Subscriber.onError]].
   *
   * @param subscriber the [[Subscriber]] that will consume signals from this [[Publisher]]
   */
  def subscribe(subscriber: Subscriber[T]): Unit

  /**
   * Request the publisher to start emitting data.
   *
   * This is a factory method and can be called multiple times, each time
   * starting a new [[Subscription]]. Each [[Subscription]] will work
   * for only a single [[Subscriber]]. A [[Subscriber]] should only
   * subscribe once to a single [[Publisher]].
   *
   * If the [[Publisher]] rejects the subscription attempt or otherwise fails
   * it will signal the error via [[Subscriber.onError]].
   *
   * This function is "unsafe" to call because it does not protect the calls to the
   * given [[Subscriber]] implementation in regards to unexpected exceptions that
   * violate the contract, therefore the given instance must respect its contract
   * and not throw any exceptions when the publisher calls `onSubscribe`, `onNext`,
   * `onComplete` and `onError`. If it does, then the behavior is undefined.
   *
   * @param subscriber the [[Subscriber]] that will consume signals from this [[Publisher]]
   */
  def unsafeSubscribe(subscriber: Subscriber[T]): Unit
}

object Publisher {
  /**
   * Converts an [[monifu.reactive.Observable Observable]] to a [[Publisher]]
   * that respects the [[http://www.reactive-streams.org/ Reactive Streams]]
   * contract.
   *
   * @param observable the source observable that must act as a publisher
   */
  def from[T](observable: Observable[T]): Publisher[T] = {
    new Publisher[T] {
      def unsafeSubscribe(subscriber: Subscriber[T]): Unit = {
        observable.unsafeSubscribe(
          Observer.from(subscriber)(observable.scheduler))
      }

      def subscribe(subscriber: Subscriber[T]): Unit = {
        observable.unsafeSubscribe(
          SafeObserver(Observer.from(subscriber)(observable.scheduler))(observable.scheduler))
      }
    }
  }
}
