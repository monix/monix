/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
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

package monifu.reactive.streams

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Observer}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Wraps a [[Subscriber Subscriber]] instance that respects the
 * [[http://www.reactive-streams.org/ Reactive Streams]] contract
 * into an [[monifu.reactive.Observer Observer]] instance that respect the `Observer`
 * contract.
 */
final class SubscriberAsObserver[T] private (subscriber: Subscriber[T])(implicit ec: ExecutionContext)
  extends Observer[T] {

  @volatile private[this] var isCanceled = false
  private[this] var isFirstEvent = true
  private[this] val leftToPush = Atomic(0)
  private[this] val requestPromise = Atomic(Promise[Ack]())

  def onNext(elem: T): Future[Ack] = {
    // on the first event sent, we need to create the subscription
    // to our underlying Subscriber by calling onSubscribe and then
    // we must block for demand to be sent
    if (isFirstEvent) {
      isFirstEvent = false
      val firstPromise = Promise[Ack]()
      // create the subscription
      subscriber.onSubscribe(createSubscription(firstPromise))

      // back-pressure until a request(n) or a cancel() comes in
      firstPromise.future.flatMap {
        case Continue if !isCanceled => onNext(elem)
        case _ => Cancel
      }
    }
    else if (isCanceled) {
      Cancel
    }
    else if (leftToPush.countDownToZero() > 0) {
      subscriber.onNext(elem)
      Continue
    }
    else {
      // Applying back-pressure until request(n) or cancel()
      // NOTE: if the contract is broken and concurrent onNext
      // events come in then this has concurrency problems
      requestPromise.getAndSet(Promise()).future.flatMap {
        case Continue if !isCanceled => onNext(elem) // retry
        case _ => Cancel
      }
    }
  }

  private[this] def createSubscription(firstPromise: Promise[Ack]) =
    new Subscription {
      private[this] val isFirst = Atomic(true)

      @tailrec
      def request(n: Int): Unit = if (!isCanceled) {
        require(n > 0, "n must be strictly positive, according to the Reactive Streams contract")

        // the very first request call must complete a different
        // promise, because we don't want the subscription implementation
        // to be in charge of updating the `requestPromise` atomic
        val promise =
          if (isFirst.get && isFirst.compareAndSet(expect=true, update=false))
            firstPromise
          else
            requestPromise.get

        val currentDemand = leftToPush.get
        if (!leftToPush.compareAndSet(currentDemand, currentDemand + n))
          request(n)
        else if (currentDemand == 0)
          promise.trySuccess(Continue)
      }

      def cancel(): Unit = {
        leftToPush lazySet 0
        isCanceled = true
        requestPromise.get.trySuccess(Cancel)
      }
    }

  def onError(ex: Throwable): Unit = {
    if (!isCanceled) {
      isCanceled = true
      subscriber.onError(ex)
    }
  }

  def onComplete(): Unit = {
    if (!isCanceled) {
      isCanceled = true
      subscriber.onComplete()
    }
  }
}

object SubscriberAsObserver {
  /**
   * Given a [[Subscriber]] as defined by the the [[http://www.reactive-streams.org/ Reactive Streams]]
   * specification, it builds an [[Observer]] instance compliant with the Monifu Rx implementation.
   */
  def apply[T](subscriber: Subscriber[T])(implicit ec: ExecutionContext): SubscriberAsObserver[T] = {
    new SubscriberAsObserver[T](subscriber)
  }
}