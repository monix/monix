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

import monix.execution.exceptions.APIContractViolationException
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.{CacheUntilConnectSubscriber, Subscriber}

import scala.concurrent.Future

/** A `GroupedObservable` is an observable type generated
  * by `Observable.groupBy`. It has the following properties:
  *
  * - comes accompanied with a `key` property after which
  *   the grouping was made
  *
  * - supports a single subscriber, throwing `IllegalStateException`
  *   if you attempt multiple subscriptions
  */
abstract class GroupedObservable[K, +V] extends Observable[V] { self =>
  /** Returns the key associated with this grouped observable. */
  def key: K
}

object GroupedObservable {
  /** Builder returning an input+output pair */
  private[monix] def broadcast[K,V](key: K, onCancel: Cancelable)
    (implicit s: Scheduler): (Subscriber[V], GroupedObservable[K,V]) = {

    val ref = new Implementation[K,V](key, onCancel)
    (ref, ref)
  }

  /** Implementation for [[GroupedObservable]] */
  private final class Implementation[K, V](val key: K, onCancel: Cancelable)
    (implicit val scheduler: Scheduler)
    extends GroupedObservable[K,V] with Subscriber[V] { self =>

    // needs to be set upon subscription
    private[this] var ref: Subscriber[V] = _
    private[this] val underlying = {
      val o = new Subscriber[V] {
        implicit val scheduler = self.scheduler
        private[this] var isDone = false

        def onNext(elem: V) = {
          val cache = ref
          val downstream = if (cache == null) self.synchronized(ref) else cache
          downstream.onNext(elem).syncOnStopOrFailure(_ => onCancel.cancel())
        }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            val cache = ref
            val downstream = if (cache == null) self.synchronized(ref) else cache
            downstream.onError(ex)
          }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            val cache = ref
            val downstream = if (cache == null) self.synchronized(ref) else cache
            downstream.onComplete()
          }
      }

      CacheUntilConnectSubscriber(o)
    }

    def onNext(elem: V): Future[Ack] = underlying.onNext(elem)
    def onError(ex: Throwable): Unit = underlying.onError(ex)
    def onComplete(): Unit = underlying.onComplete()

    def unsafeSubscribeFn(subscriber: Subscriber[V]): Cancelable =
      self.synchronized {
        if (ref != null) {
          subscriber.onError(APIContractViolationException("GroupedObservable does not support multiple subscribers"))
          Cancelable.empty
        } else {
          ref = subscriber
          val connecting = underlying.connect()
          Cancelable { () =>
            try onCancel.cancel()
            finally connecting.cancel()
          }
        }
      }
  }
}
