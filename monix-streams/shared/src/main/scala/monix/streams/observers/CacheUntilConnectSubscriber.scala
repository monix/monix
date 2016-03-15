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

package monix.streams.observers

import monix.execution.Ack.{Cancel, Continue}
import monix.execution.{Ack, Cancelable}
import monix.streams.Observable
import scala.collection.mutable
import scala.concurrent.{Future, Promise}

/** Wraps an `underlying` [[Subscriber]] into an implementation that caches
  * all events until the call to `connect()` happens. After being connected,
  * the buffer is drained into the `underlying` observer, after which all
  * subsequent events are pushed directly.
  */
final class CacheUntilConnectSubscriber[-T] private (downstream: Subscriber[T])
  extends Subscriber[T] { self =>

  implicit val scheduler = downstream.scheduler
  // MUST BE synchronized by `self`, only available if isConnected == false
  private[this] var queue = mutable.ArrayBuffer.empty[T]
  // MUST BE synchronized by `self`
  private[this] var isConnectionStarted = false
  // MUST BE synchronized by `self`, as long as isConnected == false
  private[this] var wasCanceled = false

  // Promise guaranteed to be fulfilled once isConnected is
  // seen as true and used for back-pressure.
  // MUST BE synchronized by `self`, only available if isConnected == false
  private[this] val connectedPromise = Promise[Ack]()
  private[this] var connectedFuture = connectedPromise.future

  // Volatile that is set to true once the buffer is drained.
  // Once visible as true, it implies that the queue is empty
  // and has been drained and thus the onNext/onError/onComplete
  // can take the fast path
  @volatile private[this] var isConnected = false

  // Only accessible in `connect()`
  private[this] var connectionRef: Cancelable = null

  /** Connects the underling observer to the upstream publisher.
    *
    * This function should be idempotent. Calling it multiple times should have the same
    * effect as calling it once.
    */
  def connect(): Cancelable = self.synchronized {
    if (!isConnected && !isConnectionStarted) {
      isConnectionStarted = true

      connectionRef = Observable.fromIterable(queue).unsafeSubscribeFn(new Subscriber[T] {
        implicit val scheduler = downstream.scheduler
        private[this] val bufferWasDrained = Promise[Ack]()
        private[this] var ack: Future[Ack] = Continue

        bufferWasDrained.future.onSuccess {
          case Continue =>
            connectedPromise.success(Continue)
            isConnected = true
            // GC relief
            queue = null
            connectionRef = Cancelable.empty

          case Cancel =>
            wasCanceled = true
            connectedPromise.success(Cancel)
            isConnected = true
            // GC relief
            queue = null
            connectionRef = Cancelable.empty
        }

        def onNext(elem: T): Future[Ack] = {
          ack = downstream.onNext(elem).syncOnCancelFollow(bufferWasDrained, Cancel)
          ack
        }

        def onComplete(): Unit = {
          // Applying back-pressure, otherwise the next onNext might
          // break the back-pressure contract.
          ack.syncOnContinue(bufferWasDrained.trySuccess(Continue))
        }

        def onError(ex: Throwable): Unit = {
          if (bufferWasDrained.trySuccess(Cancel))
            downstream.onError(ex)
          else
            scheduler.reportFailure(ex)
        }
      })
    }

    connectionRef
  }

  def onNext(elem: T): Future[Ack] = {
    if (!isConnected) self.synchronized {
      // checking again because of multi-threading concerns
      if (!isConnected && !isConnectionStarted) {
        // we can cache the incoming event
        queue.append(elem)
        Continue
      }
      else {
        // if the connection started, we cannot modify the queue anymore
        // so we must be patient and apply back-pressure
        connectedFuture = connectedFuture.flatMap {
          case Cancel => Cancel
          case Continue =>
            downstream.onNext(elem)
        }

        connectedFuture
      }
    }
    else if (!wasCanceled) {
      // taking fast path :-)
      downstream.onNext(elem)
    }
    else {
      // was canceled either during connect, or the upstream publisher
      // sent an onNext event after onComplete / onError
      Cancel
    }
  }

  def onComplete(): Unit = {
    // we cannot take a fast path here
    connectedFuture.syncTryFlatten
      .syncOnContinue(downstream.onComplete())
  }

  def onError(ex: Throwable): Unit = {
    // we cannot take a fast path here
    connectedFuture.syncTryFlatten
      .syncOnContinue(downstream.onError(ex))
  }
}

object CacheUntilConnectSubscriber {
  /** Builder for [[CacheUntilConnectSubscriber]] */
  def apply[T](underlying: Subscriber[T]): CacheUntilConnectSubscriber[T] =
    new CacheUntilConnectSubscriber(underlying)
}