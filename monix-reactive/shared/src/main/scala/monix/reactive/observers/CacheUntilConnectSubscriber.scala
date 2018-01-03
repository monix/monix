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

import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, CancelableFuture}
import monix.reactive.Observable

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/** Wraps an `underlying` [[Subscriber]] into an implementation that caches
  * all events until the call to `connect()` happens. After being connected,
  * the buffer is drained into the `underlying` observer, after which all
  * subsequent events are pushed directly.
  */
final class CacheUntilConnectSubscriber[-A] private (downstream: Subscriber[A])
  extends Subscriber[A] { self =>

  implicit val scheduler = downstream.scheduler
  // MUST BE synchronized by `self`, only available if isConnected == false
  private[this] var queue = mutable.ArrayBuffer.empty[A]
  // MUST BE synchronized by `self`
  private[this] var isConnectionStarted = false
  // MUST BE synchronized by `self`, as long as isConnected == false
  private[this] var wasCanceled = false

  // Promise guaranteed to be fulfilled once isConnected is
  // seen as true and used for back-pressure.
  // MUST BE synchronized by `self`, only available if isConnected == false
  private[this] var connectedPromise = Promise[Ack]()
  private[this] var connectedFuture = connectedPromise.future

  // Volatile that is set to true once the buffer is drained.
  // Once visible as true, it implies that the queue is empty
  // and has been drained and thus the onNext/onError/onComplete
  // can take the fast path
  @volatile private[this] var isConnected = false

  // Only accessible in `connect()`
  private[this] var connectionRef: CancelableFuture[Ack] = null

  /** Connects the underling observer to the upstream publisher.
    *
    * Until this call happens, the underlying observer will not receive
    * any events. Instead all incoming events are cached. And after
    * `connect` the cached events will be fed in the underlying
    * subscriber and afterwards we connect the underlying subscriber
    * directly to the upstream source.
    *
    * This function should be idempotent. Calling it multiple times
    * should have the same effect as calling it once.
    */
  def connect(): CancelableFuture[Ack] = self.synchronized {
    if (!isConnected && !isConnectionStarted) {
      isConnectionStarted = true
      val bufferWasDrained = Promise[Ack]()

      val cancelable = Observable.fromIterable(queue).unsafeSubscribeFn(new Subscriber[A] {
        implicit val scheduler = downstream.scheduler
        private[this] var ack: Future[Ack] = Continue

        bufferWasDrained.future.onComplete {
          case Success(Continue) =>
            connectedPromise.success(Continue)
            isConnected = true
            // GC relief
            queue = null
            connectedPromise = null
            // This might be a race condition problem, but it only
            // matters for GC relief purposes
            connectionRef = CancelableFuture.successful(Continue)

          case Success(Stop) =>
            wasCanceled = true
            connectedPromise.success(Stop)
            isConnected = true
            // GC relief
            queue = null
            connectedPromise = null
            // This might be a race condition problem, but it only
            // matters for GC relief purposes
            connectionRef = CancelableFuture.successful(Stop)

          case Failure(ex) =>
            wasCanceled = true
            connectedPromise.failure(ex)
            isConnected = true
            // GC relief
            queue = null
            connectedPromise = null
            // This might be a race condition problem, but it only
            // matters for GC relief purposes
            connectionRef = CancelableFuture.failed(ex)
        }

        def onNext(elem: A): Future[Ack] = {
          ack = downstream.onNext(elem).syncOnStopFollow(bufferWasDrained, Stop)
          ack
        }

        def onComplete(): Unit = {
          // Applying back-pressure, otherwise the next onNext might
          // break the back-pressure contract.
          ack.syncOnContinue(bufferWasDrained.trySuccess(Continue))
        }

        def onError(ex: Throwable): Unit = {
          if (bufferWasDrained.trySuccess(Stop))
            downstream.onError(ex)
          else
            scheduler.reportFailure(ex)
        }
      })

      connectionRef = CancelableFuture(bufferWasDrained.future, cancelable)
    }

    connectionRef
  }

  /** The [[Subscriber.onNext]] method that pushes events to
    * the underlying subscriber.
    *
    * It will back-pressure by means of its `Future[Ack]` result
    * until [[connect]] happens and the underlying queue of
    * cached events have been drained.
    */
  def onNext(elem: A): Future[Ack] = {
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
          case Stop => Stop
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
      Stop
    }
  }

  /** The [[Subscriber.onComplete]] method that pushes the
    * complete event to the underlying observer.
    *
    * It will wait for [[connect]] to happen and the queue of
    * cached events to be drained.
    */
  def onComplete(): Unit = {
    // we cannot take a fast path here
    connectedFuture.syncTryFlatten
      .syncOnContinue(downstream.onComplete())
  }

  /** The [[Subscriber.onError]] method that pushes an
    * error event to the underlying observer.
    *
    * It will wait for [[connect]] to happen and the queue of
    * cached events to be drained.
    */
  def onError(ex: Throwable): Unit = {
    // we cannot take a fast path here
    connectedFuture.syncTryFlatten
      .syncOnContinue(downstream.onError(ex))
  }
}

object CacheUntilConnectSubscriber {
  /** Builder for [[CacheUntilConnectSubscriber]] */
  def apply[A](underlying: Subscriber[A]): CacheUntilConnectSubscriber[A] =
    new CacheUntilConnectSubscriber(underlying)
}