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
import monix.execution.{Ack, CancelableFuture, Scheduler}
import monix.reactive.Observable

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/** Wraps a [[Subscriber]] into an implementation that abstains from emitting items until the call
  * to `connect()` happens. Prior to `connect()` you can enqueue
  * events for delivery once `connect()` happens, but before any items
  * emitted by `onNext` / `onComplete` and `onError`.
  *
  * Example: {{{
  *   val out = ConnectableSubscriber(subscriber)
  *
  *   // schedule onNext event, after connect()
  *   out.onNext("c")
  *
  *   // schedule event "a" to be emitted first
  *   out.pushFirst("a")
  *   // schedule event "b" to be emitted second
  *   out.pushFirst("b")
  *
  *   // underlying observer now gets events "a", "b", "c" in order
  *   out.connect()
  * }}}
  *
  * Example of an observer ended in error: {{{
  *   val out = ConnectableSubscriber(subscriber)
  *
  *   // schedule onNext event, after connect()
  *   out.onNext("c")
  *
  *   out.pushFirst("a") // event "a" to be emitted first
  *   out.pushFirst("b") // event "b" to be emitted second
  *
  *   // schedule an onError sent downstream, once connect()
  *   // happens, but after "a" and "b"
  *   out.pushError(new RuntimeException())
  *
  *   // underlying observer receives ...
  *   // onNext("a") -> onNext("b") -> onError(RuntimeException)
  *   out.connect()
  *
  *   // NOTE: that onNext("c") never happens
  * }}}
  */
final class ConnectableSubscriber[-A] private (underlying: Subscriber[A])
  extends Subscriber[A] { self =>

  implicit val scheduler: Scheduler =
    underlying.scheduler

  // MUST BE synchronized by `self`, only available if isConnected == false
  private[this] var queue = mutable.ArrayBuffer.empty[A]
  // MUST BE synchronized by `self`, only available if isConnected == false
  private[this] var scheduledDone = false
  // MUST BE synchronized by `self`, only available if isConnected == false
  private[this] var scheduledError = null : Throwable
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
    * This function should be idempotent. Calling it multiple times
    * should have the same effect as calling it once.
    */
  def connect(): CancelableFuture[Ack] =
    self.synchronized {
      if (!isConnected && !isConnectionStarted) {
        isConnectionStarted = true
        val bufferWasDrained = Promise[Ack]()

        val cancelable = Observable.fromIterable(queue).unsafeSubscribeFn(new Subscriber[A] {
          implicit val scheduler = underlying.scheduler
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
            ack = underlying.onNext(elem).syncOnStopFollow(bufferWasDrained, Stop)
            ack
          }

          def onComplete(): Unit = {
            if (!scheduledDone) {
              ack.syncOnContinue(bufferWasDrained.trySuccess(Continue))
            }
            else if (scheduledError ne null) {
              if (bufferWasDrained.trySuccess(Stop))
                underlying.onError(scheduledError)
            }
            else if (bufferWasDrained.trySuccess(Stop))
              underlying.onComplete()
          }

          def onError(ex: Throwable): Unit = {
            if (scheduledError ne null)
              scheduler.reportFailure(ex)
            else {
              scheduledDone = true
              scheduledError = ex

              if (bufferWasDrained.trySuccess(Stop))
                underlying.onError(ex)
              else
                scheduler.reportFailure(ex)
            }
          }
        })

        connectionRef = CancelableFuture(bufferWasDrained.future, cancelable)
      }

      connectionRef
    }

  /** Schedule one element to be pushed to the underlying subscriber
    * when [[connect]] happens.
    *
    * The given elements are appended to a queue that will be
    * drained on [[connect]]. Afterwards no more elements are
    * allowed to be pushed in the queue.
    *
    * These elements are streamed before any elements that will
    * eventually get streamed with [[onNext]], because of
    * the applied back-pressure from `onNext`.
    */
  def pushFirst(elem: A): Unit =
    self.synchronized {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushFirst")
      else if (!scheduledDone)
        queue += elem
    }

  /** Schedule elements to be pushed to the underlying subscriber
    * when [[connect]] happens.
    *
    * The given elements are appended to a queue that will be
    * drained on [[connect]]. Afterwards no more elements are
    * allowed to be pushed in the queue.
    *
    * These elements are streamed before any elements that will
    * eventually get streamed with [[onNext]], because of
    * the applied back-pressure from `onNext`.
    */
  def pushFirstAll[U <: A](xs: TraversableOnce[U]): Unit =
    self.synchronized {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushFirst")
      else if (!scheduledDone)
        queue.appendAll(xs)
    }

  /** Schedule a complete event when [[connect]] happens,
    * but before any elements scheduled with [[pushFirst]]
    * or [[pushFirstAll]].
    *
    * After `pushComplete` no more [[pushFirst]] or [[onNext]]
    * events are accepted.
    */
  def pushComplete(): Unit =
    self.synchronized {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushFirst")
      else if (!scheduledDone) {
        scheduledDone = true
      }
    }

  /** Schedule an error event when [[connect]] happens,
    * but before any elements scheduled with [[pushFirst]]
    * or [[pushFirstAll]].
    *
    * After `pushError` no more [[pushFirst]] or [[onNext]]
    * events are accepted.
    */
  def pushError(ex: Throwable): Unit =
    self.synchronized {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushFirst")
      else if (!scheduledDone) {
        scheduledDone = true
        scheduledError = ex
      }
    }

  /** The [[Subscriber.onNext]] method that pushes events to
    * the underlying subscriber.
    *
    * It will back-pressure by means of its `Future[Ack]` result
    * until [[connect]] happens and the underlying queue of
    * scheduled events have been drained.
    */
  def onNext(elem: A): Future[Ack] = {
    if (!isConnected) {
      // no need for synchronization here, since this reference is initialized
      // before the subscription happens and because it gets written only in
      // onNext / onComplete, which are non-concurrent clauses
      connectedFuture = connectedFuture.flatMap {
        case Continue => underlying.onNext(elem)
        case Stop => Stop
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
      Stop
    }
  }

  /** The [[Subscriber.onComplete]] method that pushes the
    * complete event to the underlying observer.
    *
    * It will wait for [[connect]] to happen and the queue of
    * scheduled events to be drained.
    */
  def onComplete(): Unit = {
    // we cannot take a fast path here
    connectedFuture.syncTryFlatten
      .syncOnContinue(underlying.onComplete())
  }

  /** The [[Subscriber.onError]] method that pushes an
    * error event to the underlying observer.
    *
    * It will wait for [[connect]] to happen and the queue of
    * scheduled events to be drained.
    */
  def onError(ex: Throwable): Unit = {
    // we cannot take a fast path here
    connectedFuture.syncTryFlatten
      .syncOnContinue(underlying.onError(ex))
  }
}

object ConnectableSubscriber {
  /** `ConnectableSubscriber` builder */
  def apply[A](subscriber: Subscriber[A]): ConnectableSubscriber[A] = {
    new ConnectableSubscriber[A](subscriber)
  }
}