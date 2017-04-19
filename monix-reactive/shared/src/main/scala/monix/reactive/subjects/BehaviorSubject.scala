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

package monix.reactive.subjects

import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.internal.util.PromiseCounter
import monix.reactive.observers.{ConnectableSubscriber, Subscriber}
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.Success

/** `BehaviorSubject` when subscribed, will emit the most recently emitted item by the source,
  * or the `initialValue` (as the seed) in case no value has yet been emitted, then continuing
  * to emit events subsequent to the time of invocation.
  *
  * When the source terminates in error, the `BehaviorSubject` will not emit any items to
  * subsequent subscribers, but instead it will pass along the error notification.
  *
  * @see [[Subject]]
  */
final class BehaviorSubject[T] private (initialValue: T)
  extends Subject[T,T] { self =>

  private[this] val stateRef =
    Atomic(BehaviorSubject.State[T](initialValue))

  def size: Int =
    stateRef.get.subscribers.size

  @tailrec
  def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
    import subscriber.scheduler
    val state = stateRef.get

    if (state.errorThrown != null) {
      subscriber.onError(state.errorThrown)
      Cancelable.empty
    }
    else if (state.isDone) {
      Observable.now(state.cached)
        .unsafeSubscribeFn(subscriber)
    }
    else {
      val c = ConnectableSubscriber(subscriber)
      val newState = state.addNewSubscriber(c)

      if (stateRef.compareAndSet(state, newState)) {
        c.pushFirst(state.cached)
        val connecting = c.connect()

        val cancelable = Cancelable { () => removeSubscriber(c) }
        connecting.syncOnStopOrFailure(_ => cancelable.cancel())
        cancelable
      }
      else {
        // retry
        unsafeSubscribeFn(subscriber)
      }
    }
  }

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    val state = stateRef.get

    if (state.isDone) Stop else {
      val newState = state.cacheElem(elem)
      if (!stateRef.compareAndSet(state, newState)) {
        onNext(elem) // retry
      }
      else {
        val iterator = state.subscribers.iterator
        // counter that's only used when we go async, hence the null
        var result: PromiseCounter[Continue.type] = null

        while (iterator.hasNext) {
          val subscriber = iterator.next()
          // using the scheduler defined by each subscriber
          import subscriber.scheduler

          val ack = try subscriber.onNext(elem) catch {
            case NonFatal(ex) => Future.failed(ex)
          }

          // if execution is synchronous, takes the fast-path
          if (ack.isCompleted) {
            // subscriber canceled or triggered an error? then remove
            if (ack != Continue && ack.value.get != Continue.AsSuccess)
              removeSubscriber(subscriber)
          }
          else {
            // going async, so we've got to count active futures for final Ack
            // the counter starts from 1 because zero implies isCompleted
            if (result == null) result = PromiseCounter(Continue, 1)
            result.acquire()

            ack.onComplete {
              case Success(Continue) =>
                result.countdown()
              case _ =>
                // subscriber canceled or triggered an error? then remove
                removeSubscriber(subscriber)
                result.countdown()
            }
          }
        }

        // has fast-path for completely synchronous invocation
        if (result == null) Continue else {
          result.countdown()
          result.future
        }
      }
    }
  }

  override def onError(ex: Throwable): Unit =
    onCompleteOrError(ex)

  override def onComplete(): Unit =
    onCompleteOrError(null)

  @tailrec
  private def onCompleteOrError(ex: Throwable): Unit = {
    val state = stateRef.get

    if (!state.isDone) {
      if (!stateRef.compareAndSet(state, state.markDone(ex)))
        onCompleteOrError(ex)
      else {
        val iterator = state.subscribers.iterator
        while (iterator.hasNext) {
          val ref = iterator.next()

          if (ex != null)
            ref.onError(ex)
          else
            ref.onComplete()
        }
      }
    }
  }

  @tailrec
  private def removeSubscriber(s: ConnectableSubscriber[T]): Unit = {
    val state = stateRef.get
    val newState = state.removeSubscriber(s)
    if (!stateRef.compareAndSet(state, newState))
      removeSubscriber(s)
  }
}

object BehaviorSubject {
  /** Builder for [[BehaviorSubject]] */
  def apply[T](initialValue: T): BehaviorSubject[T] =
    new BehaviorSubject[T](initialValue)

  /** Internal state for [[BehaviorSubject]] */
  private final case class State[T](
    cached: T,
    subscribers: Set[ConnectableSubscriber[T]] = Set.empty[ConnectableSubscriber[T]],
    isDone: Boolean = false,
    errorThrown: Throwable = null) {

    def cacheElem(elem: T): State[T] = {
      copy(cached = elem)
    }

    def addNewSubscriber(s: ConnectableSubscriber[T]): State[T] =
      copy(subscribers = subscribers + s)

    def removeSubscriber(toRemove: ConnectableSubscriber[T]): State[T] = {
      val newSet = subscribers - toRemove
      copy(subscribers = newSet)
    }

    def markDone(ex: Throwable): State[T] = {
      copy(subscribers = Set.empty, isDone = true, errorThrown = ex)
    }
  }
}
