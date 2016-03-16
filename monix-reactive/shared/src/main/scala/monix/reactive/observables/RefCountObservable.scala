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

import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.sincron.atomic.Atomic
import scala.annotation.tailrec
import scala.concurrent.Future

/** A `RefCountObservable` is an observable that wraps a
  * [[ConnectableObservable]], initiating the connection on the first
  * `subscribe()` and then staying connected as long as there is at least
  * one subscription active.
  *
  * @param source - the connectable observable we are wrapping
  */
final class RefCountObservable[+T] private (source: ConnectableObservable[T])
  extends Observable[T] {

  private[this] val refs = Atomic(-1)
  private[this] lazy val connection: Cancelable =
    source.connect()

  @tailrec
  def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
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
    } else {
      implicit val s = subscriber.scheduler
      val ret = source.unsafeSubscribeFn(wrap(subscriber))
      if (current == -1) connection // triggers connect()

      Cancelable { () =>
        try ret.cancel() finally tryCancel()
      }
    }
  }

  private def wrap[U >: T](downstream: Subscriber[U]): Subscriber[U]  = {
    new Subscriber[U] {
      implicit val scheduler = downstream.scheduler

      def onNext(elem: U): Future[Ack] = {
        downstream.onNext(elem)
          .syncOnCancelOrFailure(tryCancel())
      }

      def onError(ex: Throwable): Unit = {
        try downstream.onError(ex) finally
          tryCancel()
      }

      def onComplete(): Unit = {
        try downstream.onComplete() finally
          tryCancel()
      }
    }
  }

  @tailrec
  private def tryCancel(): Boolean = refs.get match {
    case x if x > 0 =>
      val update = x-1
      if (!refs.compareAndSet(x, update))
        tryCancel()
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
