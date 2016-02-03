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

import monix.execution.Scheduler
import monix.streams._
import monix.streams.Ack.{Cancel, Continue}
import monix.streams.broadcast.Subject
import monix.streams.internal.FutureAckExtensions
import scala.collection.mutable
import scala.concurrent.{Future, Promise}


/** Wraps a [[Subscriber]] into an implementation that abstains from emitting items until the call
  * to `connect()` happens. Prior to `connect()` it's also a [[Subject]] into which you can enqueue
  * events for delivery once `connect()` happens, but before any items
  * emitted by `onNext` / `onComplete` and `onError`.
  *
  * Example: {{{
  *   val obs = ConnectableObserver(observer)
  *
  *   // schedule onNext event, after connect()
  *   obs.onNext("c")
  *
  *   // schedule event "a" to be emitted first
  *   obs.pushFirst("a")
  *   // schedule event "b" to be emitted second
  *   obs.pushFirst("b")
  *
  *   // underlying observer now gets events "a", "b", "c" in order
  *   obs.connect()
  * }}}
  *
  * Example of an observer ended in error: {{{
  *   val obs = ConnectableObserver(observer)
  *
  *   // schedule onNext event, after connect()
  *   obs.onNext("c")
  *
  *   obs.pushFirst("a") // event "a" to be emitted first
  *   obs.pushFirst("b") // event "b" to be emitted second
  *
  *   // schedule an onError sent downstream, once connect()
  *   // happens, but after "a" and "b"
  *   obs.pushError(new RuntimeException())
  *
  *   // underlying observer receives ...
  *   // onNext("a") -> onNext("b") -> onError(RuntimeException)
  *   obs.connect()
  *
  *   // NOTE: that onNext("c") never happens
  * }}}
  */
final class ConnectableSubscriber[-T] private (underlying: Subscriber[T])
  extends Subscriber[T] { self =>

  private[this] val lock = new AnyRef
  implicit def scheduler: Scheduler =
    underlying.scheduler

  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] var queue = mutable.ArrayBuffer.empty[T]
  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] var scheduledDone = false
  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] var scheduledError = null : Throwable
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
  def connect(): Unit =
    lock.synchronized {
      if (!isConnected && !isConnectionStarted) {
        isConnectionStarted = true

        Observable.fromIterable(queue).unsafeSubscribeFn(new Observer[T] {
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
            underlying.onNext(elem)
              .ifCancelTryCanceling(bufferWasDrained)
          }

          def onComplete(): Unit = {
            if (!scheduledDone) {
              bufferWasDrained.trySuccess(Continue)
            }
            else if (scheduledError ne null) {
              if (bufferWasDrained.trySuccess(Cancel))
                underlying.onError(scheduledError)
            }
            else if (bufferWasDrained.trySuccess(Cancel))
              underlying.onComplete()
          }

          def onError(ex: Throwable): Unit = {
            if (scheduledError ne null)
              scheduler.reportFailure(ex)
            else {
              scheduledDone = true
              scheduledError = ex

              if (bufferWasDrained.trySuccess(Cancel))
                underlying.onError(ex)
              else
                scheduler.reportFailure(ex)
            }
          }
        })
      }
    }

  /**
    * Emit an item immediately to the underlying observer,
    * after previous `pushFirst()` events, but before any events emitted through
    * `onNext`.
    */
  def pushFirst(elems: T*) =
    lock.synchronized {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushFirst")
      else if (!scheduledDone)
        queue.append(elems : _*)
    }

  /* Internal method for pushing a whole [[Buffer]] */
  private[monix] def pushIterable[U <: T](iterable: Iterable[U]) =
    lock.synchronized {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushFirst")
      else if (!scheduledDone) {
        val cursor = iterable.iterator
        while (cursor.hasNext)
          queue.append(cursor.next())
      }
    }

  /**
    * Emit an item
    */
  def pushComplete() =
    lock.synchronized {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushFirst")
      else if (!scheduledDone) {
        scheduledDone = true
      }
    }

  def pushError(ex: Throwable) =
    lock.synchronized {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushFirst")
      else if (!scheduledDone) {
        scheduledDone = true
        scheduledError = ex
      }
    }

  def onNext(elem: T) = {
    if (!isConnected) {
      // no need for synchronization here, since this reference is initialized
      // before the subscription happens and because it gets written only in
      // onNext / onComplete, which are non-concurrent clauses
      connectedFuture = connectedFuture.flatMap {
        case Cancel => Cancel
        case Continue =>
          underlying.onNext(elem)
      }
      connectedFuture
    }
    else if (!wasCanceled) {
      // taking fast path
      underlying.onNext(elem)
    }
    else {
      // was canceled either during connect, or the upstream publisher
      // sent an onNext event after onComplete / onError
      Cancel
    }
  }

  def onComplete() = {
    // we cannot take a fast path here
    connectedFuture.onContinueSignalComplete(underlying)
  }

  def onError(ex: Throwable) = {
    // we cannot take a fast path here
    connectedFuture.onContinueSignalError(underlying, ex)
  }
}

object ConnectableSubscriber {
  /** `ConnectableSubscriber` builder */
  def apply[T](subscriber: Subscriber[T]): ConnectableSubscriber[T] = {
    new ConnectableSubscriber[T](subscriber)
  }
}