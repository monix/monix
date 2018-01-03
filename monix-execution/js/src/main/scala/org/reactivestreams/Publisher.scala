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

package org.reactivestreams

/**
 * Mirrors the `Publisher` interface from the
 * [[http://www.reactive-streams.org/ Reactive Streams]] project.
 *
 * A `Publisher` is a provider of a potentially unbounded number of sequenced
 * elements, publishing them according to the demand received from its
 * [[Subscriber Subscribers]].
 *
 * A `Publisher` can serve multiple Subscribers subscribed
 * dynamically at various points in time.
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
   * @param subscriber the [[Subscriber]] that will consume signals
   *                   from this [[Publisher]]
   */
  def subscribe(subscriber: Subscriber[_ >: T]): Unit
}
