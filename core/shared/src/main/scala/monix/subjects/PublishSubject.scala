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
 
package monix.subjects

import scalax.concurrent.atomic.padded.Atomic
import monix.Ack.{Continue, Cancel}
import monix.internal.PromiseCounter
import monix.subjects.PublishSubject.State
import monix.{Ack, Subject, Subscriber}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.control.NonFatal

/** A `PublishSubject` emits to a subscriber only those items that are
  * emitted by the source subsequent to the time of the subscription
  *
  * If the source terminates with an error, the `PublishSubject` will not emit any
  * items to subsequent subscribers, but will simply pass along the error
  * notification from the source Observable.
  *
  * @see [[monix.Subject]]
  */
final class PublishSubject[T] private () extends Subject[T,T] {
  /*
   * NOTE: the stored vector value can be null and if it is, then
   * that means our subject has been terminated.
   */
  private[this] val stateRef = Atomic(State[T]())

  private
  def onSubscribeCompleted(subscriber: Subscriber[T], ex: Throwable): Unit = {
    if (ex != null) subscriber.onError(ex) else
      subscriber.onComplete()
  }

  /*
   * NOTE: onSubscribe is in contention with onNext, onComplete and onError,
   * thus access to the subscribers/isDone state is done through CAS on an
   * Atomic and if the new subscriber must be fed an initial set of events,
   * then we enforce a happens-before relationship by means of the
   * FreezeOnFirstOnNextSubscriber (the purpose of calling onSubscribeContinue)
   */
  @tailrec
  def unsafeSubscribeFn(subscriber: Subscriber[T]): Unit = {
    val state = stateRef.get
    val subscribers = state.subscribers

    if (subscribers eq null) {
      // our subject was completed, taking fast path
      onSubscribeCompleted(subscriber, state.errorThrown)
    }
    else {
      // this subscriber type can freeze our `onNext` until
      // it's been fed with our buffer
      val update = State(subscribers :+ subscriber)

      if (!stateRef.compareAndSet(state, update))
        unsafeSubscribeFn(subscriber) // repeat
    }
  }

  def onNext(elem: T): Future[Ack] = {
    val state = stateRef.get
    // at some point we are going to notice the most recent subscribers
    val subscribers = state.subscribers

    if (subscribers eq null) Cancel else {
      val iterator = subscribers.iterator
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
          if (ack != Continue && ack.value.get != Continue.IsSuccess)
            unsubscribe(subscriber)
        }
        else {
          // going async, so we've got to count active futures for final Ack
          // the counter starts from 1 because zero implies isCompleted
          if (result == null) result = PromiseCounter(Continue, 1)
          result.acquire()

          ack.onComplete {
            case Continue.IsSuccess =>
              result.countdown()

            case _ =>
              // subscriber canceled or triggered an error? then remove
              unsubscribe(subscriber)
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

  def onError(ex: Throwable): Unit = {
    onCompleteOrError(ex)
  }

  def onComplete(): Unit = {
    onCompleteOrError(null)
  }

  @tailrec
  private def onCompleteOrError(ex: Throwable): Unit = {
    val state = stateRef.get
    val subscribers = state.subscribers
    val isDone = subscribers eq null

    if (!isDone) {
      // because of this CAS operation we are guaranteed to observe
      // the most recent set of subscribers that may contain references
      // that haven't been seen in onNext yet
      if (!stateRef.compareAndSet(state, state.complete(ex)))
        onCompleteOrError(ex)
      else {
        val iterator = subscribers.iterator
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
  private def unsubscribe(subscriber: Subscriber[T]): Continue = {
    val state = stateRef.get
    val subscribers = state.subscribers

    if (subscribers eq null) Continue else {
      val update = State(subscribers.filterNot(_ == subscriber))
      if (!stateRef.compareAndSet(state, update))
        unsubscribe(subscriber) // retry
      else
        Continue
    }
  }
}

object PublishSubject {
  /** Builder for [[PublishSubject]] */
  def apply[T](): PublishSubject[T] =
    new PublishSubject[T]()

  /** Synchronized state for [[PublishSubject]].
    *
    * NOTE: `subscribers` can be `null`.
    *
    * @param subscribers is the set of subscribers that are currently subscribed
    * @param errorThrown is the error received in `onError`, or `null` if no error
    */
  private[subjects] final case class State[-T](
    subscribers: Vector[Subscriber[T]] = Vector.empty,
    errorThrown: Throwable = null) {

    def isDone: Boolean = {
      subscribers eq null
    }

    def complete(errorThrown: Throwable): State[T] = {
      if (subscribers eq null) this else
        State[T](null, errorThrown)
    }
  }
}
