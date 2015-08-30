/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.observables

import monifu.concurrent.Scheduler
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.reactive.observers.CacheUntilConnectSubscriber
import monifu.reactive.{Observable, Subject, Subscriber}

/**
 * Represents an [[monifu.reactive.Observable Observable]] that waits for
 * the call to `connect()` before
 * starting to emit elements to its subscriber(s).
 *
 * Useful for converting cold observables into hot observables and thus returned by
 * [[monifu.reactive.Observable.multicast Observable.multicast]].
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
  def refCount(): Observable[T] = {
    RefCountObservable(self)
  }

  protected
  def liftToSelf[U](f: Observable[T] => Observable[U]): ConnectableObservable[U] =
    new ConnectableObservable[U] {
      private[this] val lifted = f(self)
      def connect() = self.connect()
      def onSubscribe(subscriber: Subscriber[U]): Unit =
        lifted.onSubscribe(subscriber)
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

      def onSubscribe(subscriber: Subscriber[R]): Unit = {
        subject.onSubscribe(subscriber)
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

      def onSubscribe(subscriber: Subscriber[R]): Unit = {
        subject.onSubscribe(subscriber)
      }
    }
  }
}