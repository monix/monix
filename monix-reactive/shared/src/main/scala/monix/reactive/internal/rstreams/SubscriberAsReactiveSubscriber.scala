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

package monix.reactive.internal.rstreams

import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.rstreams.SingleAssignmentSubscription
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import org.reactivestreams.{Subscriber => RSubscriber, Subscription => RSubscription}
import scala.concurrent.Future

/** Wraps a [[monix.reactive.Observer Observer]] instance into an
  * `org.reactiveSubscriber` instance. The resulting
  * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
  * contract.
  *
  * Given that when emitting [[monix.reactive.Observer.onNext Observer.onNext]] calls,
  * the call may pass asynchronous boundaries, the emitted events need to be buffered.
  * The `requestCount` constructor parameter also represents the buffer size.
  *
  * To async an instance, [[SubscriberAsReactiveSubscriber]] must be used: {{{
  *   // uses the default requestCount of 128
  *   val subscriber = SubscriberAsReactiveSubscriber(new Observer[Int] {
  *     private[this] var sum = 0
  *
  *     def onNext(elem: Int) = {
  *       sum += elem
  *       Continue
  *     }
  *
  *     def onError(ex: Throwable) = {
  *       logger.error(ex)
  *     }
  *
  *     def onComplete() = {
  *       logger.info("Stream completed")
  *     }
  *   })
  * }}}
  *
  * @param target the observer instance that will get wrapped into a
  *                   `org.reactiveSubscriber`, along with the scheduler used
  * @param requestCount the parameter passed to `Subscription.request`,
  *                    also representing the buffer size; MUST BE strictly positive
  */
private[monix] final class SubscriberAsReactiveSubscriber[A] private
  (target: Subscriber[A], requestCount: Int)
  extends RSubscriber[A] {

  require(requestCount > 0, "requestCount must be strictly positive, according to the Reactive Streams contract")

  private[this] val subscription = SingleAssignmentSubscription()
  private[this] val downstream: Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = target.scheduler

      private[this] val isFinite = requestCount < Int.MaxValue
      private[this] var isActive = true
      private[this] var toReceive = requestCount

      locally {
        // Requesting the first batch
        subscription.request(if (isFinite) requestCount else Long.MaxValue)
      }

      private def continue(): Continue = {
        toReceive -= 1
        if (toReceive <= 0) {
          toReceive = requestCount
          subscription.request(requestCount)
        }
        Continue
      }

      private def stop(): Stop = {
        isActive = false
        subscription.cancel()
        Stop
      }

      private def finiteOnNext(elem: A): Future[Ack] =
        target.onNext(elem).syncTryFlatten match {
          case Continue => continue()
          case Stop => stop()
          case async =>
            async.transform(
              ack => ack match {
                case Continue => continue()
                case Stop => stop()
              },
              err => {
                stop()
                err
              })(immediate)
        }

      def onNext(elem: A): Future[Ack] = {
        if (isActive) {
          if (isFinite) finiteOnNext(elem) else target.onNext(elem)
        }
        else
          Stop
      }

      def onError(ex: Throwable): Unit =
        if (isActive) { isActive = false; target.onError(ex) }
      def onComplete(): Unit =
        if (isActive) { isActive = false; target.onComplete() }
    }


  private[this] val buffer: Subscriber.Sync[A] =
    BufferedSubscriber.synchronous(downstream, Unbounded)

  def onSubscribe(s: RSubscription): Unit =
    subscription := s

  def onNext(elem: A): Unit = {
    if (elem == null)
      throw new NullPointerException(
        "onNext(null) is forbidden, see rule 2.13 in the Reactive Streams spec")

    buffer.onNext(elem)
  }

  def onError(ex: Throwable): Unit =
    buffer.onError(ex)

  def onComplete(): Unit =
    buffer.onComplete()
}


private[monix] object SubscriberAsReactiveSubscriber {
  /**
    * Wraps a [[monix.reactive.Observer Observer]] instance into a
    * `org.reactiveSubscriber` instance. The resulting
    * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
    * contract.
    *
    * Given that when emitting [[monix.reactive.Observer.onNext Observer.onNext]] calls,
    * the call may pass asynchronous boundaries, the emitted events need to be buffered.
    * The `requestCount` constructor parameter also represents the buffer size.
    *
    * To async an instance, [[SubscriberAsReactiveSubscriber.apply]] must be used: {{{
    *   // uses the default requestCount of 128
    *   val subscriber = SubscriberAsReactiveSubscriber(new Observer[Int] {
    *     private[this] var sum = 0
    *
    *     def onNext(elem: Int) = {
    *       sum += elem
    *       Continue
    *     }
    *
    *     def onError(ex: Throwable) = {
    *       logger.error(ex)
    *     }
    *
    *     def onComplete() = {
    *       logger.info("Stream completed")
    *     }
    *   })
    * }}}
    *
    * @param subscriber the subscriber instance that will get wrapped into a
    *                  `org.reactiveSubscriber`
    * @param requestCount the parameter passed to each `Subscription.request` call,
    *                    also representing the buffer size; MUST BE strictly positive
    */
  def apply[T](subscriber: Subscriber[T], requestCount: Int = 128): RSubscriber[T] =
    subscriber match {
      case ref: Subscriber.Sync[_] =>
        SyncSubscriberAsReactiveSubscriber(ref.asInstanceOf[Subscriber.Sync[T]], requestCount)
      case _ =>
        new SubscriberAsReactiveSubscriber[T](subscriber, requestCount)
    }
}

/**
  * Wraps a [[monix.reactive.observers.Subscriber.Sync Subscriber.Sync]] instance into a
  * `org.reactiveSubscriber` instance. The resulting
  * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
  * contract.
  *
  * Given that we can guarantee a [[monix.reactive.observers.Subscriber.Sync Subscriber.Sync]]
  * is used, then no buffering is needed and thus the implementation is very efficient.
  *
  * To async an instance, [[SyncSubscriberAsReactiveSubscriber]] must be used: {{{
  *   // uses the default requestCount of 128
  *   val subscriber = SyncSubscriberAsReactiveSubscriber(new Observer[Int] {
  *     private[this] var sum = 0
  *
  *     def onNext(elem: Int) = {
  *       sum += elem
  *       Continue
  *     }
  *
  *     def onError(ex: Throwable) = {
  *       logger.error(ex)
  *     }
  *
  *     def onComplete() = {
  *       logger.info("Stream completed")
  *     }
  *   })
  * }}}
  */
private[monix] final class SyncSubscriberAsReactiveSubscriber[T] private
  (target: Subscriber.Sync[T], requestCount: Int)
  extends RSubscriber[T] {

  require(requestCount > 0, "requestCount must be strictly positive, according to the Reactive Streams contract")

  private[this] implicit val s = target.scheduler

  private[this] var subscription = null : RSubscription
  private[this] var expectingCount = 0L
  @volatile private[this] var isCanceled = false

  def onSubscribe(s: RSubscription): Unit = {
    if (subscription == null && !isCanceled) {
      subscription = s
      expectingCount = requestCount
      s.request(requestCount)
    }
    else {
      s.cancel()
    }
  }

  def onNext(elem: T): Unit = {
    if (subscription == null)
      throw new NullPointerException(
        "onSubscription never happened, see rule 2.13 in the Reactive Streams spec")
    if (elem == null)
      throw new NullPointerException(
        "onNext(null) is forbidden, see rule 2.13 in the Reactive Streams spec")

    if (!isCanceled) {
      if (expectingCount > 0) expectingCount -= 1

      target.onNext(elem) match {
        case Continue =>
          // should it request more events?
          if (expectingCount == 0) {
            expectingCount = requestCount
            subscription.request(requestCount)
          }
        case Stop =>
          // downstream canceled, so we MUST cancel too
          isCanceled = true
          subscription.cancel()
      }
    }
  }

  def onError(ex: Throwable): Unit = {
    if (ex == null) throw new NullPointerException(
      "onError(null) is forbidden, see rule 2.13 in the Reactive Streams spec")

    if (!isCanceled) {
      isCanceled = true
      target.onError(ex)
    }
  }

  def onComplete(): Unit =
    if (!isCanceled) {
      isCanceled = true
      target.onComplete()
    }
}


private[monix] object SyncSubscriberAsReactiveSubscriber {
  /**
    * Wraps a [[monix.reactive.observers.Subscriber.Sync Subscriber.Sync]] instance into a
    * `org.reactiveSubscriber` instance. The resulting
    * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
    * contract.
    *
    * Given that we can guarantee a [[monix.reactive.observers.Subscriber.Sync Subscriber.Sync]]
    * is used, then no buffering is needed and thus the implementation is very efficient.
    *
    * To async an instance, [[SyncSubscriberAsReactiveSubscriber.apply]] must be used: {{{
    *   // uses the default requestCount of 128
    *   val subscriber = SyncSubscriberAsReactiveSubscriber(new Observer[Int] {
    *     private[this] var sum = 0
    *
    *     def onNext(elem: Int) = {
    *       sum += elem
    *       Continue
    *     }
    *
    *     def onError(ex: Throwable) = {
    *       logger.error(ex)
    *     }
    *
    *     def onComplete() = {
    *       logger.info("Stream completed")
    *     }
    *   })
    * }}}
    *
    * @param target the observer instance that will get wrapped into a
    *                  `org.reactiveSubscriber`, along with the
    *                  used scheduler
    * @param requestCount the parameter passed to `Subscription.request`
    */
  def apply[T](target: Subscriber.Sync[T], requestCount: Int = 128): RSubscriber[T] =
    new SyncSubscriberAsReactiveSubscriber[T](target, requestCount)
}
