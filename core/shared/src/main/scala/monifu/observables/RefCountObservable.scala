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

package monifu.observables

import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.{Cancelable, Scheduler}
import monifu.internal._
import monifu.{Observable, Observer, Subscriber}
import scala.annotation.tailrec


/**
 * A `RefCountObservable` is an observable that wraps a
 * [[ConnectableObservable]], initiating the connection on the first
 * `subscribe()` and then staying connected as long as there is at least
 * one subscription active.
 *
 * @param source - the connectable observable we are wrapping
 */
final class RefCountObservable[+T] private (source: ConnectableObservable[T])
  extends Observable[T] {

  private[this] val refs = Atomic(-1)
  private[this] lazy val connection =
    source.connect()

  @tailrec
  def unsafeSubscribeFn(subscriber: Subscriber[T]): Unit = {
    val current = refs.get
    val update = current match {
      case x if x < 0 => 1
      case 0 => 0
      case x => x + 1
    }

    if (update == 0) {
      source.unsafeSubscribeFn(subscriber)
    }
    else if (!refs.compareAndSet(current, update)) {
      // retry
      unsafeSubscribeFn(subscriber)
    }
    else {
      implicit val s = subscriber.scheduler
      val cancelable = BooleanCancelable(cancel())
      source.unsafeSubscribeFn(observer(cancelable, subscriber))
      if (current == -1) connection // triggers connect()
    }
  }
  
  private def observer[U >: T](cancelable: Cancelable, downstream: Observer[U])
    (implicit s: Scheduler): Observer[U]  = {
    
    new Observer[U] {
      def onNext(elem: U) = {
        downstream.onNext(elem)
          .ifCanceledDoCancel(cancelable)
      }

      def onError(ex: Throwable): Unit = {
        try downstream.onError(ex) finally
          cancelAll()
      }

      def onComplete(): Unit = {
        try downstream.onComplete() finally
          cancelAll()
      }
    }
  }

  private def cancelAll(): Unit = {
    while (cancel()) {}
  }

  @tailrec
  private def cancel(): Boolean = refs.get match {
    case x if x > 0 =>
      val update = x-1
      if (!refs.compareAndSet(x, update))
        cancel()
      else if (update == 0) {
        connection.cancel()
        true
      }
      else
        true
    case 0 => false
    case negative =>
      throw new IllegalStateException(s"refs=$negative (after init)")
  }
}

object RefCountObservable {
  /** Builder for [[RefCountObservable]] */
  def apply[T](connectable: ConnectableObservable[T]): Observable[T] =
    new RefCountObservable(connectable)
}