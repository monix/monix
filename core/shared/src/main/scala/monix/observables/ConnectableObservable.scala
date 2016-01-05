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

package monix.observables

import scalax.concurrent.Scheduler
import scalax.concurrent.cancelables.BooleanCancelable
import monix.observers.CacheUntilConnectSubscriber
import monix.{Observable, Subject, Subscriber}

/**
 * Represents an [[monix.Observable Observable]] that waits for
 * the call to `connect()` before
 * starting to emit elements to its subscriber(s).
 *
 * Useful for converting cold observables into hot observables and thus returned by
 * [[monix.Observable.multicast Observable.multicast]].
 */
trait ConnectableObservable[+T] extends Observable[T]
  with LiftOperators1[T, ConnectableObservable] { self =>

  /**
   * Starts emitting events to subscribers.
   */
  def connect(): BooleanCancelable

  /**
   * Returns an [[Observable]] that stays connected to this
   * `ConnectableObservable` as long as there is at least one
   * subscription that is active.
   */
  def refCount: Observable[T] = {
    RefCountObservable(self)
  }

  protected
  def liftToSelf[U](f: Observable[T] => Observable[U]): ConnectableObservable[U] =
    new ConnectableObservable[U] {
      private[this] val lifted = f(self)
      def connect() = self.connect()
      def unsafeSubscribeFn(subscriber: Subscriber[U]): Unit =
        lifted.unsafeSubscribeFn(subscriber)
    }
}


object ConnectableObservable {
  /**
   * Builds a [[ConnectableObservable]] for the given observable source
   * and a given [[Subject]].
   */
  def apply[T, R](source: Observable[T], subject: Subject[T, R])
      (implicit s: Scheduler): ConnectableObservable[R] = {

    new ConnectableObservable[R] {
      private[this] lazy val connection = {
        source.subscribe(subject)
      }

      def connect() = {
        connection
      }

      def unsafeSubscribeFn(subscriber: Subscriber[R]): Unit = {
        subject.unsafeSubscribeFn(subscriber)
      }
    }
  }

  /**
   * Creates a [[ConnectableObservable]] that takes elements from `source`
   * and caches them until the call to `connect()` happens. After that
   * the events are piped through the given `subject` to the final
   * subscribers.
   */
  def cacheUntilConnect[T, R](source: Observable[T], subject: Subject[T, R])
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

      def connect() = {
        connection
      }

      def unsafeSubscribeFn(subscriber: Subscriber[R]): Unit = {
        subject.unsafeSubscribeFn(subscriber)
      }
    }
  }
}