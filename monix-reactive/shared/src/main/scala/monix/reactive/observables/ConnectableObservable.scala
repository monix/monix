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

package monix.reactive.observables

import monix.execution.{Cancelable, Scheduler}
import monix.reactive.observers.{CacheUntilConnectSubscriber, Subscriber}
import monix.reactive.subjects.Subject
import monix.reactive.{Observable, Pipe}

/** Represents an [[monix.reactive.Observable Observable]] that waits for
  * the call to `connect()` before
  * starting to emit elements to its subscriber(s).
  *
  * Represents a hot observable (an observable that shares its data-source
  * to multiple subscribers).
  */
abstract class ConnectableObservable[+A] extends Observable[A] { self =>
  /** Starts emitting events to subscribers. */
  def connect(): Cancelable

  /** Returns an [[Observable]] that stays connected to this
    * `ConnectableObservable` as long as there is at least one
    * subscription that is active.
    */
  final def refCount: Observable[A] = {
    RefCountObservable(self)
  }
}

object ConnectableObservable {
  /** Builds a [[ConnectableObservable]] for the given observable source
    * and a given [[monix.reactive.subjects.Subject Subject]].
    */
  def unsafeMulticast[A, B](source: Observable[A], subject: Subject[A, B])
    (implicit s: Scheduler): ConnectableObservable[B] = {

    new ConnectableObservable[B] {
      private[this] lazy val connection: Cancelable =
        source.unsafeSubscribeFn(Subscriber(subject, s))

      def connect(): Cancelable =
        connection

      def unsafeSubscribeFn(subscriber: Subscriber[B]): Cancelable =
        subject.unsafeSubscribeFn(subscriber)
    }
  }

  /** Builds a [[ConnectableObservable]] for the given observable source
    * and a given [[Pipe]].
    */
  def multicast[A, B](source: Observable[A], recipe: Pipe[A, B])
    (implicit s: Scheduler): ConnectableObservable[B] = {

    new ConnectableObservable[B] {
      private[this] val (input, output) = recipe.multicast(s)
      private[this] lazy val connection = {
        source.subscribe(input)
      }

      def connect(): Cancelable =
        connection

      def unsafeSubscribeFn(subscriber: Subscriber[B]): Cancelable =
        output.unsafeSubscribeFn(subscriber)
    }
  }

  /** Creates a [[ConnectableObservable]] that takes elements from `source`
    * and caches them until the call to `connect()` happens. After that
    * the events are piped through the given `subject` to the final
    * subscribers.
    */
  def cacheUntilConnect[A, B](source: Observable[A], subject: Subject[A, B])
    (implicit s: Scheduler): ConnectableObservable[B] = {

    new ConnectableObservable[B] {
      private[this] val (connectable, cancelRef) = {
        val ref = CacheUntilConnectSubscriber(Subscriber(subject, s))
        val c = source.unsafeSubscribeFn(ref) // connects immediately
        (ref, c)
      }

      private[this] lazy val connection = {
        val connecting = connectable.connect()
        Cancelable { () =>
          try cancelRef.cancel()
          finally connecting.cancel()
        }
      }

      def connect(): Cancelable =
        connection

      def unsafeSubscribeFn(subscriber: Subscriber[B]): Cancelable =
        subject.unsafeSubscribeFn(subscriber)
    }
  }
}