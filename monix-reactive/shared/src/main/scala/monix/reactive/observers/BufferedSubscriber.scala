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

package monix.reactive.observers

import monix.reactive.OverflowStrategy
import monix.reactive.observers.buffers.BuildersImpl

/** Interface describing [[monix.reactive.Observer Observer]] wrappers
  * that are thread-safe (can receive concurrent events) and that
  * return an immediate `Continue` when receiving `onNext`
  * events. Meant to be used by data sources that cannot uphold the
  * no-concurrent events and the back-pressure related requirements
  * (i.e. data-sources that cannot wait on `Future[Ack]` for
  * sending the next event).
  *
  * Implementations of this interface have the following contract:
  *
  *  - `onNext` / `onError` / `onComplete` of this interface MAY be
  *    called concurrently
  *  - `onNext` SHOULD return an immediate `Continue`, as long as the
  *    buffer is not full and the underlying observer hasn't signaled
  *    `Stop` (N.B. due to the asynchronous nature, `Stop` signaled
  *    by the underlying observer may be noticed later, so
  *    implementations of this interface make no guarantee about queued
  *    events - which could be generated, queued and dropped on the
  *    floor later)
  *  - `onNext` MUST return an immediate `Stop` result, after it
  *    notices that the underlying observer signaled `Stop` (due to
  *    the asynchronous nature of observers, this may happen later and
  *    queued events might get dropped on the floor)
  *  - in general the contract for the underlying Observer is fully
  *    respected (grammar, non-concurrent notifications, etc...)
  *  - when the underlying observer canceled (by returning `Stop`),
  *    or when a concurrent upstream data source triggered an error,
  *    this SHOULD eventually be noticed and acted upon
  *  - as long as the buffer isn't full and the underlying observer
  *    isn't `Stop`, then implementations of this interface SHOULD
  *    not lose events in the process
  *  - the buffer MAY BE either unbounded or bounded, in case of
  *    bounded buffers, then an appropriate overflowStrategy needs to be set for
  *    when the buffer overflows - either an `onError` triggered in the
  *    underlying observer coupled with a `Stop` signaled to the
  *    upstream data sources, or dropping events from the head or the
  *    tail of the queue, or attempting to apply back-pressure, etc...
  *
  * See [[OverflowStrategy OverflowStrategy]] for the buffer
  * policies available.
  */
trait BufferedSubscriber[-A] extends Subscriber[A]

private[reactive] trait Builders {
  /** Given an [[OverflowStrategy]] wraps a [[Subscriber]] into a
    * buffered subscriber.
    */
  def apply[A](subscriber: Subscriber[A], bufferPolicy: OverflowStrategy[A]): Subscriber[A]

  /** Given an synchronous [[OverflowStrategy overflow strategy]] wraps
    * a [[Subscriber]] into a buffered subscriber.
    */
  def synchronous[A](subscriber: Subscriber[A], bufferPolicy: OverflowStrategy.Synchronous[A]): Subscriber.Sync[A]

  /** Builds a batched buffered subscriber.
    *
    * A batched buffered subscriber buffers incoming events while
    * the `underlying` is busy and then sends a whole sequence at once.
    *
    * The underlying buffer size will be able to support at least
    * `maxSize` items. When the `maxSize` is reached, the subscriber
    * will back-pressure the source.
    *
    * So a batched buffered subscriber is implicitly delivering
    * the back-pressure overflow strategy.
    */
  def batched[A](underlying: Subscriber[List[A]], bufferSize: Int): Subscriber[A]
}

object BufferedSubscriber extends Builders with BuildersImpl
