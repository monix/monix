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

package monifu.reactive.streams

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.OverflowStrategy.Unbounded
import monifu.reactive.observers.{BufferedSubscriber, SynchronousSubscriber}
import org.reactivestreams.{Subscriber, Subscription}

/**
 * Wraps a [[monifu.reactive.Observer Observer]] instance into an
 * `org.reactivestreams.Subscriber` instance. The resulting
 * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
 * contract.
 *
 * Given that when emitting [[monifu.reactive.Observer.onNext Observer.onNext]] calls,
 * the call may pass asynchronous boundaries, the emitted events need to be buffered.
 * The `requestCount` constructor parameter also represents the buffer size.
 *
 * To create an instance, [[SubscriberAsReactiveSubscriber]] must be used: {{{
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
 * @param subscriber the observer instance that will get wrapped into a
 *                   `org.reactivestreams.Subscriber`, along with the scheduler used
 *
 * @param requestCount the parameter passed to `Subscription.request`,
 *                    also representing the buffer size; MUST BE strictly positive
 */
final class SubscriberAsReactiveSubscriber[T] private
    (subscriber: monifu.reactive.Subscriber[T], requestCount: Int)
  extends Subscriber[T] {

  require(requestCount > 0, "requestCount must be strictly positive, according to the Reactive Streams contract")

  private[this] val buffer =
    SynchronousSubscriberAsReactiveSubscriber(
      BufferedSubscriber.synchronous(subscriber, Unbounded),
      requestCount = requestCount)

  def onSubscribe(s: Subscription): Unit =
    buffer.onSubscribe(s)

  def onNext(elem: T): Unit = {
    if (elem == null) throw new NullPointerException("onNext(null)")
    buffer.onNext(elem)
  }

  def onError(ex: Throwable): Unit =
    buffer.onError(ex)

  def onComplete(): Unit =
    buffer.onComplete()
}


object SubscriberAsReactiveSubscriber {
  /**
   * Wraps a [[monifu.reactive.Observer Observer]] instance into a
   * `org.reactivestreams.Subscriber` instance. The resulting
   * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
   * contract.
   *
   * Given that when emitting [[monifu.reactive.Observer.onNext Observer.onNext]] calls,
   * the call may pass asynchronous boundaries, the emitted events need to be buffered.
   * The `requestCount` constructor parameter also represents the buffer size.
   *
   * To create an instance, [[SubscriberAsReactiveSubscriber.apply]] must be used: {{{
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
   *                  `org.reactivestreams.Subscriber`
   *
   * @param requestCount the parameter passed to each `Subscription.request` call,
   *                    also representing the buffer size; MUST BE strictly positive
   */
  def apply[T](subscriber: monifu.reactive.Subscriber[T], requestCount: Int = 128): Subscriber[T] =
    subscriber match {
      case ref: SynchronousSubscriber[_] =>
        SynchronousSubscriberAsReactiveSubscriber(ref.asInstanceOf[SynchronousSubscriber[T]], requestCount)
      case _ =>
        new SubscriberAsReactiveSubscriber[T](subscriber, requestCount)
    }
}

/**
 * Wraps a [[monifu.reactive.observers.SynchronousObserver SynchronousObserver]] instance into a
 * `org.reactivestreams.Subscriber` instance. The resulting
 * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
 * contract.
 *
 * Given that we can guarantee a [[monifu.reactive.observers.SynchronousObserver SynchronousObserver]]
 * is used, then no buffering is needed and thus the implementation is very efficient.
 *
 * To create an instance, [[SynchronousSubscriberAsReactiveSubscriber]] must be used: {{{
 *   // uses the default requestCount of 128
 *   val subscriber = SynchronousSubscriberAsReactiveSubscriber(new Observer[Int] {
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
final class SynchronousSubscriberAsReactiveSubscriber[T] private
    (subscriber: SynchronousSubscriber[T], requestCount: Int)
  extends Subscriber[T] {

  require(requestCount > 0, "requestCount must be strictly positive, according to the Reactive Streams contract")

  private[this] implicit val s = subscriber.scheduler

  private[this] var subscription = null : Subscription
  private[this] var expectingCount = 0
  @volatile private[this] var isCanceled = false

  def onSubscribe(s: Subscription): Unit = {
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
    if (subscription == null) throw new NullPointerException("onSubscription never happened")
    if (elem == null) throw new NullPointerException("onNext(null)")

    if (!isCanceled) {
      if (expectingCount > 0) expectingCount -= 1

      subscriber.onNext(elem) match {
        case Continue =>
          // should it request more events?
          if (expectingCount == 0) {
            expectingCount = requestCount
            subscription.request(requestCount)
          }
        case Cancel =>
          // downstream canceled, so we MUST cancel too
          isCanceled = true
          subscription.cancel()
      }
    }
  }

  def onError(ex: Throwable): Unit = {
    if (ex == null) throw new NullPointerException("onError(null)")

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


object SynchronousSubscriberAsReactiveSubscriber {
  /**
   * Wraps a [[monifu.reactive.observers.SynchronousObserver SynchronousObserver]] instance into a
   * `org.reactivestreams.Subscriber` instance. The resulting
   * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
   * contract.
   *
   * Given that we can guarantee a [[monifu.reactive.observers.SynchronousObserver SynchronousObserver]]
   * is used, then no buffering is needed and thus the implementation is very efficient.
   *
   * To create an instance, [[SynchronousSubscriberAsReactiveSubscriber.apply]] must be used: {{{
   *   // uses the default requestCount of 128
   *   val subscriber = SynchronousSubscriberAsReactiveSubscriber(new Observer[Int] {
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
   * @param subscriber the observer instance that will get wrapped into a
   *                  `org.reactivestreams.Subscriber`, along with the
   *                  used scheduler
   *
   * @param requestCount the parameter passed to `Subscription.request`
   */
  def apply[T](subscriber: SynchronousSubscriber[T], requestCount: Int = 128): Subscriber[T] = {
    new SynchronousSubscriberAsReactiveSubscriber[T](subscriber, requestCount)
  }
}