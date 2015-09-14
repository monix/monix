/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.subjects

import monifu.concurrent.atomic.padded.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals.PromiseCounter
import monifu.reactive.{Ack, Subject, Subscriber}
import monifu.reactive.subjects.GenericSubject.State
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * Non-blocking implementation of a subject with reusable logic shared
 * between `PublishSubject`, `BehaviorSubject` and `ReplaySubject`.
 */
protected[reactive] abstract class GenericSubject[T]
  extends Subject[T,T] {

  /*
   * NOTE: the stored vector value can be null and if it is, then
   * that means our subject has been terminated.
   */
  private[this] val stateRef = Atomic(State[T]())

  /** Invoked on each onNext, giving the possibility of caching the signal */
  protected def cacheOrIgnore(elem: T): Unit

  /** Invoked on a new subscriber in case the subject is already completed */
  protected def onSubscribeCompleted(s: Subscriber[T], ex: Throwable): Unit

  /** Underlying type of the subscriber we are using */
  protected type LiftedSubscriber <: Subscriber[T]

  /**
   * For wrapping the subscriber into something that can be
   * used in `onSubscribeContinue`
   */
  protected def liftSubscriber(ref: Subscriber[T]): LiftedSubscriber

  /**
   * Called in `onSubscribe` in case the subject is not terminated,
   * giving the possibility of populating the subscriber with an initial
   * set of events before connecting it to the stream of incoming events.
   */
  protected def onSubscribeContinue(lifted: LiftedSubscriber, s: Subscriber[T]): Unit

  /*
   * NOTE: onSubscribe is in contention with onNext, onComplete and onError,
   * thus access to the subscribers/isDone state is done through CAS on an
   * Atomic and if the new subscriber must be fed an initial set of events,
   * then we enforce a happens-before relationship by means of the
   * FreezeOnFirstOnNextSubscriber (the purpose of calling onSubscribeContinue)
   */
  @tailrec
  final def onSubscribe(subscriber: Subscriber[T]): Unit = {
    val state = stateRef.get
    val subscribers = state.subscribers

    if (subscribers eq null) {
      // our subject was completed, taking fast path
      onSubscribeCompleted(subscriber, state.errorThrown)
    }
    else {
      // this subscriber type can freeze our `onNext` until
      // it's been fed with our buffer
      val liftedSubscriber = liftSubscriber(subscriber)
      val update = State(subscribers :+ liftedSubscriber)

      if (!stateRef.compareAndSet(state, update))
        onSubscribe(subscriber) // repeat
      else
        onSubscribeContinue(liftedSubscriber, subscriber)
    }
  }

  final def onNext(elem: T): Future[Ack] = {
    val state = stateRef.get
    // at some point we are going to notice the most recent subscribers
    val subscribers = state.subscribers

    if (subscribers eq null) Cancel else {
      cacheOrIgnore(elem)

      val iterator = subscribers.iterator
      // counter that's only used when we go async, hence the null
      var result: PromiseCounter[Continue.type] = null

      while (iterator.hasNext) {
        val subscriber = iterator.next()
        // using the scheduler defined by each subscriber

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

  final def onError(ex: Throwable): Unit = {
    onCompleteOrError(ex)
  }

  final def onComplete(): Unit = {
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

protected[reactive] object GenericSubject {
  /**
   * State used in the atomic references of the Subject implementations.
   *
   * NOTE: `subscribers` can be `null`.
   *
   * @param subscribers is the set of subscribers that are currently subscribed
   * @param errorThrown is the error received in `onError`, or `null` if no error
   */
  final case class State[-T](
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