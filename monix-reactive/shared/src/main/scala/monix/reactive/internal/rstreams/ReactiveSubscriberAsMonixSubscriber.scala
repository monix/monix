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

package monix.reactive.internal.rstreams

import monix.execution.{Cancelable, Ack, Scheduler}
import monix.reactive.observers.Subscriber
import monix.execution.atomic.Atomic
import monix.execution.Ack.{Stop, Continue}
import monix.reactive.internal.rstreams.ReactiveSubscriberAsMonixSubscriber.RequestsQueue
import org.reactivestreams.{Subscriber => RSubscriber, Subscription}
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.{Future, Promise}

/** Wraps a `org.reactivestreams.Subscriber` instance that respects the
  * [[http://www.reactive-streams.org/ Reactive Streams]] contract
  * into an [[monix.reactive.Observer Observer]] instance that
  * respect the `Observer` contract.
  */
private[reactive] final class ReactiveSubscriberAsMonixSubscriber[A] private
  (subscriber: RSubscriber[A], subscription: Cancelable)
  (implicit val scheduler: Scheduler)
  extends Subscriber[A] with Cancelable { self =>

  if (subscriber == null) throw null

  private[this] var isComplete = false
  private[this] val requests = new RequestsQueue
  private[this] var leftToPush = 0L
  private[this] var firstEvent = true
  private[this] var ack: Future[Ack] = Continue

  def cancel(): Unit = {
    requests.cancel()
    subscription.cancel()
  }

  @tailrec
  def onNext(elem: A): Future[Ack] = {
    if (isComplete)
      Stop
    else if (firstEvent) {
      firstEvent = false
      subscriber.onSubscribe(createSubscription())
      onNext(elem) // retry
    }
    else if (leftToPush > 0) {
      leftToPush -= 1
      subscriber.onNext(elem)
      ack = Continue
      ack
    }
    else {
      ack = requests.await().flatMap { requested =>
        if (requested <= 0) Stop else {
          leftToPush += (requested - 1)
          subscriber.onNext(elem)
          Continue
        }
      }

      ack
    }
  }

  def onError(ex: Throwable): Unit =
    if (!isComplete) {
      isComplete = true
      if (firstEvent) subscriber.onSubscribe(createSubscription())
      subscriber.onError(ex)
    }

  def onComplete(): Unit =
    if (!isComplete) {
      isComplete = true
      if (firstEvent) subscriber.onSubscribe(createSubscription())
      ack.syncOnContinue(subscriber.onComplete())
    }

  private def createSubscription() = new Subscription {
    def cancel(): Unit = self.cancel()

    def request(n: Long): Unit = {
      try requests.request(n) catch {
        case ex: IllegalArgumentException =>
          subscriber.onError(ex)
      }
    }
  }
}

private[reactive] object ReactiveSubscriberAsMonixSubscriber {
  /** Given an `org.reactivestreams.Subscriber` as defined by
    * the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification, it builds an [[monix.reactive.Observer]]
    * instance compliant with the Monix Rx implementation.
    */
  def apply[A](subscriber: RSubscriber[A], subscription: Cancelable)
    (implicit s: Scheduler): ReactiveSubscriberAsMonixSubscriber[A] =
    new ReactiveSubscriberAsMonixSubscriber[A](subscriber, subscription)

  /** An asynchronous queue implementation for dealing with
    * requests from a Subscriber.
    */
  private final class RequestsQueue {
    private[this] val state = Atomic(ActiveState(Queue.empty, Queue.empty) : State)

    @tailrec
    def await(): Future[Long] = {
      state.get match {
        case CancelledState =>
          Future.successful(0)

        case oldState @ ActiveState(elements, promises) =>
          if (elements.nonEmpty) {
            val (e, newQ) = elements.dequeue
            val newState = ActiveState(newQ, promises)

            if (!state.compareAndSet(oldState, newState))
              await()
            else
              Future.successful(e)
          }
          else {
            val p = Promise[Long]()
            val newState = ActiveState(elements, promises.enqueue(p))

            if (!state.compareAndSet(oldState, newState))
              await()
            else
              p.future
          }
      }
    }

    @tailrec
    def request(n: Long): Unit = {
      require(n > 0, "n must be strictly positive, according to " +
        "the Reactive Streams contract, rule 3.9")

      state.get match {
        case CancelledState =>
          () // do nothing

        case oldState @ ActiveState(elements, promises) if promises.nonEmpty =>
          val (p, q) = promises.dequeue
          val newState = ActiveState(elements, q)

          if (!state.compareAndSet(oldState, newState))
            request(n)
          else
            p.success(n)

        case oldState @ ActiveState(Queue(requested), promises) if requested > 0 =>
          val r = requested + n
          val newState = ActiveState(Queue(r), promises)

          if (!state.compareAndSet(oldState, newState))
            request(n)

        case oldState @ ActiveState(elements, promises) =>
          val newState = ActiveState(elements.enqueue(n), promises)
          if (!state.compareAndSet(oldState, newState))
            request(n)
      }
    }

    @tailrec
    def cancel(): Unit = {
      state.get match {
        case CancelledState =>
          () // do nothing

        case oldState @ ActiveState(_, promises) =>
          if (!state.compareAndSet(oldState, CancelledState))
            cancel()
          else
            promises.foreach(_.success(0))
      }
    }

    sealed trait State

    case class ActiveState(elements: Queue[Long], promises: Queue[Promise[Long]])
      extends State

    case object CancelledState
      extends State
  }
}