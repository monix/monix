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

package monix.reactive.observables

import monix.execution.Cancelable
import monix.reactive.Observable
import monix.reactive.subjects.ReplaySubject
import monix.reactive.observers.Subscriber
import org.sincron.atomic.Atomic

/** A `CachedObservable` is an observable that wraps a regular
  * [[Observable]], initiating the connection on the first
  * `subscribe()` and then staying connected for as long as
  * the source is emitting.
  *
  * NOTE: this is NOT a [[ConnectableObservable]] and being a hot
  * data-source you've got no way to cancel the source.
  *
  * @param source - the observable we are wrapping
  * @param maxCapacity - the buffer capacity, or 0 for usage of an unbounded buffer
  */
final class CachedObservable[+T] private (source: Observable[T], maxCapacity: Int)
  extends Observable[T] {

  private[this] val isStarted = Atomic(false)
  private[this] val subject = {
    if (maxCapacity > 0) ReplaySubject.createWithSize[T](maxCapacity) else
      ReplaySubject[T]()
  }

  def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
    import subscriber.scheduler
    if (isStarted.compareAndSet(expect = false, update = true))
      source.unsafeSubscribeFn(Subscriber(subject, scheduler))
    subject.unsafeSubscribeFn(subscriber)
  }
}

object CachedObservable {
  /** Builder for [[CachedObservable]]
    *
    * @param observable - is the observable we are wrapping
    */
  def create[T](observable: Observable[T]): Observable[T] =
    new CachedObservable(observable, 0)

  /** Builder for [[CachedObservable]]
    *
    * @param observable - is the observable we are wrapping
    * @param maxCapacity - the buffer capacity, with old elements being dropped on overflow
    */
  def create[T](observable: Observable[T], maxCapacity: Int): Observable[T] = {
    require(maxCapacity > 0, "capacity must be strictly positive")
    new CachedObservable(observable, maxCapacity)
  }
}