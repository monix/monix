/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.streams

import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals.FutureAckExtensions
import monifu.reactive.{Ack, Observer}
import org.reactivestreams.{Subscriber, Subscription}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}

/**
 * Wraps a [[Subscriber Subscriber]] instance that respects the
 * [[http://www.reactive-streams.org/ Reactive Streams]] contract
 * into an [[monifu.reactive.Observer Observer]] instance that respect the `Observer`
 * contract.
 */
final class SubscriberAsObserver[T] private
    (subscriber: Subscriber[T])(implicit s: Scheduler)
  extends Observer[T] {

  @volatile private[this] var isCanceled = false
  private[this] var isFirstEvent = true
  private[this] val leftToPush = Atomic(0L)
  private[this] var ack = Continue : Future[Ack]

  // MUST only be modified by the Subscription object
  private[this] var requestPromise = Promise[Ack]()

  private[this] def retryOnNext(elem: T) = onNext(elem)

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    // on the first event sent, we need to create the subscription
    // to our underlying Subscriber by calling onSubscribe and then
    // we must block for demand to be sent
    if (isFirstEvent) {
      isFirstEvent = false
      // create the subscription
      subscriber.onSubscribe(createSubscription())
      // tail-recursive call - retry
      onNext(elem)
    }
    else if (isCanceled) {
      ack = Cancel
      ack
    }
    else if (leftToPush.countDownToZero() > 0) {
      subscriber.onNext(elem)
      ack = Continue
      ack
    }
    else {
      val result = {
        // race condition guard
        if (isCanceled) {
          Cancel
        }
        else if (leftToPush.get > 0) {
          // we've had request events in the meantime
          null
        }
        else {
          // Applying back-pressure until request(n) or cancel()
          // NOTE: if the contract is broken and concurrent onNext
          // events come in then this has concurrency problems
          requestPromise.future.flatMap {
            case Continue if !isCanceled => retryOnNext(elem)
            case _ => Cancel
          }
        }
      }

      if (result == null) onNext(elem) else {
        ack = result
        ack
      }
    }
  }

  private[this] def createSubscription(): Subscription =
    new Subscription {
      def request(n: Long): Unit = {
        if (!isCanceled) {
          require(n > 0, "n must be strictly positive, according to the Reactive Streams contract")

          if (leftToPush.getAndAdd(n) == 0) {
            val promise = requestPromise
            requestPromise = Promise()
            promise.trySuccess(Continue)
          }
        }
      }

      def cancel(): Unit = {
        leftToPush lazySet 0
        isCanceled = true
        requestPromise.trySuccess(Cancel)
      }
    }

  def onError(ex: Throwable): Unit = {
    if (!isCanceled) {
      isCanceled = true
      subscriber.onError(ex)
    }
  }

  def onComplete(): Unit = {
    if (isFirstEvent) {
      isCanceled = true
      subscriber.onComplete()
    }
    else if (!isCanceled)
      ack.onContinue {
        if (!isCanceled) {
          isCanceled = true
          subscriber.onComplete()
        }
      }
  }
}

object SubscriberAsObserver {
  /**
   * Given a [[Subscriber]] as defined by the the [[http://www.reactive-streams.org/ Reactive Streams]]
   * specification, it builds an [[Observer]] instance compliant with the Monifu Rx implementation.
   */
  def apply[T](subscriber: Subscriber[T])(implicit s: Scheduler): SubscriberAsObserver[T] = {
    new SubscriberAsObserver[T](subscriber)
  }
}