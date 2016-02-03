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

package monix.streams.observables

import monix.execution.Scheduler
import monix.execution.cancelables.BooleanCancelable
import monix.streams.broadcast.Processor
import monix.streams.observers.CacheUntilConnectSubscriber
import monix.streams.{Pipe, Observable, Subscriber}

/** Represents an [[Observable Observable]] that waits for
  * the call to `connect()` before
  * starting to emit elements to its subscriber(s).
  *
  * Useful for converting cold observables into hot observables and thus returned by
  * [[monix.streams.Observable.unsafeMulticast Observable.multicast]].
  */
trait ConnectableObservable[+T] extends Observable[T] { self =>
  /** Starts emitting events to subscribers. */
  def connect(): BooleanCancelable

  /** Returns an [[Observable]] that stays connected to this
    * `ConnectableObservable` as long as there is at least one
    * subscription that is active.
    */
  def refCount: Observable[T] = {
    RefCountObservable(self)
  }
}


object ConnectableObservable {
  /** Builds a [[ConnectableObservable]] for the given observable source
    * and a given [[Processor]].
    */
  def unsafeMulticast[T, R](source: Observable[T], pipe: Processor[T, R])
    (implicit s: Scheduler): ConnectableObservable[R] = {

    new ConnectableObservable[R] {
      private[this] lazy val connection = {
        source.subscribe(pipe)
      }

      def connect(): BooleanCancelable = {
        connection
      }

      def unsafeSubscribeFn(subscriber: Subscriber[R]): Unit = {
        pipe.unsafeSubscribeFn(subscriber)
      }
    }
  }

  /** Builds a [[ConnectableObservable]] for the given observable source
    * and a given [[Pipe]].
    */
  def multicast[T, R](source: Observable[T], recipe: Pipe[T, R])
    (implicit s: Scheduler): ConnectableObservable[R] = {

    new ConnectableObservable[R] {
      private[this] val pipe = recipe.createProcessor()
      private[this] lazy val connection = {
        source.subscribe(pipe)
      }

      def connect(): BooleanCancelable = {
        connection
      }

      def unsafeSubscribeFn(subscriber: Subscriber[R]): Unit = {
        pipe.unsafeSubscribeFn(subscriber)
      }
    }
  }

  /** Creates a [[ConnectableObservable]] that takes elements from `source`
    * and caches them until the call to `connect()` happens. After that
    * the events are piped through the given `subject` to the final
    * subscribers.
    */
  def cacheUntilConnect[T, R](source: Observable[T], subject: Processor[T, R])
    (implicit s: Scheduler): ConnectableObservable[R] = {

    new ConnectableObservable[R] {
      private[this] val (connectable, cancelRef) = {
        val ref = CacheUntilConnectSubscriber(Subscriber(subject, s))
        val c = source.subscribe(ref) // connects immediately
        (ref, c)
      }

      private[this] lazy val connection = {
        connectable.connect()
        BooleanCancelable { cancelRef.cancel() }
      }

      def connect(): BooleanCancelable = {
        connection
      }

      def unsafeSubscribeFn(subscriber: Subscriber[R]): Unit = {
        subject.unsafeSubscribeFn(subscriber)
      }
    }
  }
}