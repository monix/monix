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

package monifu.reactive.observers

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive._
import monifu.reactive.internals._

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

/**
 * Wraps an `underlying` [[Subscriber]] into an implementation that caches
 * all events until the call to `connect()` happens. After being connected,
 * the buffer is drained into the `underlying` observer, after which all
 * subsequent events are pushed directly.
 */
final class CacheUntilConnectSubscriber[-T] private (downstream: Subscriber[T])
  extends Subscriber[T] { self =>

  val scheduler = downstream.scheduler
  private[this] implicit val s = scheduler
  private[this] val lock = new AnyRef

  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] var queue = mutable.ArrayBuffer.empty[T]
  // MUST BE synchronized by `lock`
  private[this] var isConnectionStarted = false
  // MUST BE synchronized by `lock`, as long as isConnected == false
  private[this] var wasCanceled = false

  // Promise guaranteed to be fulfilled once isConnected is
  // seen as true and used for back-pressure.
  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] val connectedPromise = Promise[Ack]()
  private[this] var connectedFuture = connectedPromise.future

  // Volatile that is set to true once the buffer is drained.
  // Once visible as true, it implies that the queue is empty
  // and has been drained and thus the onNext/onError/onComplete
  // can take the fast path
  @volatile private[this] var isConnected = false

  /**
   * Connects the underling observer to the upstream publisher.
   *
   * This function should be idempotent. Calling it multiple times should have the same
   * effect as calling it once.
   */
  def connect(): Unit = lock.synchronized {
    if (!isConnected && !isConnectionStarted) {
      isConnectionStarted = true

      Observable.fromIterable(queue).onSubscribe(new Observer[T] {
        private[this] val bufferWasDrained = Promise[Ack]()

        bufferWasDrained.future.onSuccess {
          case Continue =>
            connectedPromise.success(Continue)
            isConnected = true
            queue = null // gc relief

          case Cancel =>
            wasCanceled = true
            connectedPromise.success(Cancel)
            isConnected = true
            queue = null // gc relief
        }

        def onNext(elem: T): Future[Ack] = {
          downstream.onNext(elem)
            .ifCancelTryCanceling(bufferWasDrained)
        }

        def onComplete(): Unit = {
          bufferWasDrained.trySuccess(Continue)
        }

        def onError(ex: Throwable): Unit = {
          if (bufferWasDrained.trySuccess(Continue))
            self.onError(ex)
          else
            s.reportFailure(ex)
        }
      })
    }
  }

  def onNext(elem: T) = {
    if (!isConnected) lock.synchronized {
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

  def onComplete() = {
    // we cannot take a fast path here
    connectedFuture.onContinueSignalComplete(downstream)
  }

  def onError(ex: Throwable) = {
    // we cannot take a fast path here
    connectedFuture.onContinueSignalError(downstream, ex)
  }
}

object CacheUntilConnectSubscriber {
  /** Builder for [[CacheUntilConnectSubscriber]] */
  def apply[T](underlying: Subscriber[T]): CacheUntilConnectSubscriber[T] =
    new CacheUntilConnectSubscriber(underlying)
}