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

import monix.execution.Ack.{Cancel, Continue}
import monix.execution.{Ack, Cancelable}
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.PublishSubject.State
import org.sincron.atomic.Atomic
import scala.annotation.tailrec

/** An `AsyncSubject` emits the last value (and only the last value) emitted by
  * the source and only after the source completes.
  *
  * If the source terminates with an error, the `AsyncSubject` will not emit any
  * items to subsequent subscribers, but will simply pass along the error
  * notification from the source Observable.
  */
final class AsyncSubject[T] extends Subject[T,T] { self =>
  /*
   * NOTE: the stored vector value can be null and if it is, then
   * that means our subject has been terminated.
   */
  private[this] val stateRef = Atomic(State[T]())

  private[this] var onNextHappened = false
  private[this] var cachedElem: T = _

  def onNext(elem: T): Ack = {
    if (stateRef.get.isDone) Cancel else {
      if (!onNextHappened) onNextHappened = true
      cachedElem = elem
      Continue
    }
  }

  def onError(ex: Throwable): Unit = {
    onCompleteOrError(ex)
  }

  def onComplete(): Unit = {
    onCompleteOrError(null)
  }

  @tailrec
  def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
    val state = stateRef.get
    val subscribers = state.subscribers

    if (subscribers eq null) {
      // our subject was completed, taking fast path
      val errorThrown = state.errorThrown
      if (errorThrown != null) {
        subscriber.onError(errorThrown)
        Cancelable.empty
      }
      else if (onNextHappened) {
        subscriber.onNext(cachedElem)
        subscriber.onComplete()
        Cancelable.empty
      }
      else {
        subscriber.onComplete()
        Cancelable.empty
      }
    } else {
      val update = State(subscribers + subscriber)
      if (!stateRef.compareAndSet(state, update))
        unsafeSubscribeFn(subscriber) // repeat
      else
        Cancelable(() => unsubscribe(subscriber))
    }
  }

  @tailrec
  private def onCompleteOrError(ex: Throwable): Unit = {
    val state = stateRef.get
    val subscribers = state.subscribers
    val isDone = subscribers eq null

    if (!isDone) {
      if (!stateRef.compareAndSet(state, state.complete(ex)))
        onCompleteOrError(ex)
      else {
        val iterator = subscribers.iterator
        while (iterator.hasNext) {
          val ref = iterator.next()

          if (ex != null)
            ref.onError(ex)
          else if (onNextHappened) {
            ref.onNext(cachedElem)
            ref.onComplete()
          }
          else
            ref.onComplete()
        }
      }
    }
  }

  @tailrec private def unsubscribe(s: Subscriber[T]): Unit = {
    val current = stateRef.get
    val update = current.copy(subscribers = current.subscribers - s)
    if (!stateRef.compareAndSet(current, update))
      unsubscribe(s)
  }
}

object AsyncSubject {
  def apply[T](): AsyncSubject[T] =
    new AsyncSubject[T]()
}