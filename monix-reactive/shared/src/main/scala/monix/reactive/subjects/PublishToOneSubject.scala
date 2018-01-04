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

package monix.reactive.subjects

import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.Atomic
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.APIContractViolationException
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}

/** `PublishToOneSubject` is a [[monix.reactive.subjects.PublishSubject]]
  * that can be subscribed at most once.
  *
  * In case the subject gets subscribed more than once, then the
  * subscribers will be notified with a
  * [[monix.execution.exceptions.APIContractViolationException APIContractViolationException]]
  * error.
  *
  * Given that unicast observables are tricky, for working with this subject
  * one can also be notified when the subscription finally happens.
  */
final class PublishToOneSubject[A] private () extends Subject[A,A] with BooleanCancelable {
  import PublishToOneSubject.{canceledState, pendingCompleteState}

  private[this] val subscriptionP = Promise[Ack]()
  private[this] var errorThrown: Throwable = _
  private[this] val ref = Atomic(null : Subscriber[A])

  /** A `Future` that signals when the subscription happened
    * with a `Continue`, or with a `Stop` if the subscription
    * happened but the subject was already completed.
    */
  val subscription = subscriptionP.future

  def size: Int =
    ref.get match {
      case null | `pendingCompleteState` | `canceledState` => 0
      case _ => 1
    }

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable =
    ref.get match {
      case null =>
        if (!ref.compareAndSet(null, subscriber))
          unsafeSubscribeFn(subscriber) // retry
        else {
          subscriptionP.success(Continue)
          this
        }

      case `pendingCompleteState` =>
        if (!ref.compareAndSet(pendingCompleteState, canceledState))
          unsafeSubscribeFn(subscriber)
        else if (errorThrown != null) {
          subscriber.onError(errorThrown)
          subscriptionP.success(Stop)
          Cancelable.empty
        } else {
          subscriber.onComplete()
          subscriptionP.success(Stop)
          Cancelable.empty
        }

      case _ =>
        subscriber.onError(APIContractViolationException("PublishToOneSubject does not support multiple subscribers"))
        Cancelable.empty
    }

  def onNext(elem: A): Future[Ack] =
    ref.get match {
      case null => Continue
      case subscriber =>
        subscriber.onNext(elem)
    }

  def onError(ex: Throwable): Unit = {
    errorThrown = ex
    signalComplete()
  }

  def onComplete(): Unit =
    signalComplete()

  @tailrec private def signalComplete(): Unit = {
    ref.get match {
      case null =>
        if (!ref.compareAndSet(null, pendingCompleteState))
          signalComplete() // retry
      case `pendingCompleteState` | `canceledState` =>
        () // do nothing
      case subscriber =>
        if (!ref.compareAndSet(subscriber, canceledState))
          signalComplete() // retry
        else if (errorThrown != null)
          subscriber.onError(errorThrown)
        else
          subscriber.onComplete()
    }
  }

  def isCanceled: Boolean =
    ref.get eq canceledState

  def cancel(): Unit =
    ref.set(canceledState)
}

object PublishToOneSubject {
  /** Builder for a [[PublishToOneSubject]]. */
  def apply[A](): PublishToOneSubject[A] =
    new PublishToOneSubject[A]()

  private final val canceledState = new EmptySubscriber[Any]
  private final val pendingCompleteState = new EmptySubscriber[Any]

  /** Helper for managing state in the `PublishToOneSubject` */
  private final class EmptySubscriber[-A] extends Subscriber.Sync[A] {
    implicit def scheduler: Scheduler =
      throw new IllegalStateException("EmptySubscriber.scheduler")

    def onNext(elem: A): Ack = Stop
    def onError(ex: Throwable): Unit = ()
    def onComplete(): Unit = ()
  }
}