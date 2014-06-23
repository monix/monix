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

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.Observer
import monifu.reactive.observers.{SynchronousBufferedObserver, SynchronousObserver}
import scala.concurrent.ExecutionContext

/**
 * Wraps a [[monifu.reactive.Observer Observer]] instance into an
 * [[Subscriber Subscriber]] instance. The resulting
 * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
 * contract.
 *
 * Given that when emitting [[monifu.reactive.Observer.onNext Observer.onNext]] calls,
 * the call may pass asynchronous boundaries, the emitted events need to be buffered.
 * The `requestCount` constructor parameter also represents the buffer size.
 * 
 * To create an instance, [[ObserverAsSubscriber.apply]] must be used: {{{
 *   // uses the default requestCount of 128
 *   val subscriber = ObserverAsSubscriber(new Observer[Int] {
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
 * @param observer the observer instance that will get wrapped into a
 *                 [[Subscriber Subscriber]]
 *
 * @param requestCount the parameter passed to [[Subscription.request]],
 *                    also representing the buffer size; MUST BE strictly positive
 *
 * @param ec the execution context needed for processing asynchronous `Future` results
 */
final class ObserverAsSubscriber[-T] private (observer: Observer[T], requestCount: Int)(implicit ec: ExecutionContext)
  extends Subscriber[T] {

  require(requestCount > 0, "requestCount must be strictly positive, according to the Reactive Streams contract")

  private[this] val buffer =
    SynchronousObserverAsSubscriber(
      SynchronousBufferedObserver.unbounded(observer),
      requestCount = requestCount
    )

  def onSubscribe(s: Subscription): Unit =
    buffer.onSubscribe(s)

  def onNext(elem: T): Unit =
    buffer.onNext(elem)

  def onError(ex: Throwable): Unit =
    buffer.onError(ex)

  def onComplete(): Unit =
    buffer.onComplete()
}


object ObserverAsSubscriber {
  /**
   * Wraps a [[monifu.reactive.Observer Observer]] instance into a
   * [[Subscriber Subscriber]] instance. The resulting
   * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
   * contract.
   *
   * Given that when emitting [[monifu.reactive.Observer.onNext Observer.onNext]] calls,
   * the call may pass asynchronous boundaries, the emitted events need to be buffered.
   * The `requestCount` constructor parameter also represents the buffer size.
   *
   * To create an instance, [[ObserverAsSubscriber.apply]] must be used: {{{
   *   // uses the default requestCount of 128
   *   val subscriber = ObserverAsSubscriber(new Observer[Int] {
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
   * @param observer the observer instance that will get wrapped into a
   *                 [[Subscriber Subscriber]]
   *
   * @param requestCount the parameter passed to each [[Subscription.request]] call,
   *                    also representing the buffer size; MUST BE strictly positive
   *
   * @param ec the execution context needed for processing asynchronous `Future` results
   */
  def apply[T](observer: Observer[T], requestCount: Int = 128)(implicit ec: ExecutionContext): Subscriber[T] =
    observer match {
      case ref: SynchronousObserver[_] =>
        SynchronousObserverAsSubscriber(ref.asInstanceOf[SynchronousObserver[T]], requestCount)
      case _ =>
        new ObserverAsSubscriber[T](observer, requestCount)
    }
}

/**
 * Wraps a [[monifu.reactive.observers.SynchronousObserver SynchronousObserver]] instance into a
 * [[Subscriber Subscriber]] instance. The resulting
 * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
 * contract.
 *
 * Given that we can guarantee a [[monifu.reactive.observers.SynchronousObserver SynchronousObserver]]
 * is used, then no buffering is needed and thus the implementation is very efficient.
 *
 * To create an instance, [[SynchronousObserverAsSubscriber.apply]] must be used: {{{
 *   // uses the default requestCount of 128
 *   val subscriber = SynchronousObserverAsSubscriber(new Observer[Int] {
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
 * @param observer the observer instance that will get wrapped into a
 *                 [[Subscriber Subscriber]]
 *
 * @param requestCount the parameter passed to each [[Subscription.request]] call.
 *
 * @param ec the execution context needed for processing asynchronous `Future` results
 */
final class SynchronousObserverAsSubscriber[-T] private (observer: SynchronousObserver[T], requestCount: Int)(implicit ec: ExecutionContext)
  extends Subscriber[T] {

  require(requestCount > 0, "requestCount must be strictly positive, according to the Reactive Streams contract")

  private[this] var subscription = null : Subscription
  private[this] var expectingCount = 0
  @volatile private[this] var isCanceled = false

  def onSubscribe(s: Subscription): Unit =
    if (!isCanceled) {
      subscription = s
      expectingCount = requestCount
      s.request(requestCount)
    }

  def onNext(elem: T): Unit = {
    if (!isCanceled) {
      if (expectingCount > 0) expectingCount -= 1

      observer.onNext(elem) match {
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
    if (!isCanceled) {
      isCanceled = true
      observer.onError(ex)
    }
  }

  def onComplete(): Unit = {
    if (!isCanceled) {
      isCanceled = true
      observer.onComplete()
    }
  }
}


object SynchronousObserverAsSubscriber {
  /**
   * Wraps a [[monifu.reactive.observers.SynchronousObserver SynchronousObserver]] instance into a
   * [[Subscriber Subscriber]] instance. The resulting
   * subscriber respects the [[http://www.reactive-streams.org/ Reactive Streams]]
   * contract.
   *
   * Given that we can guarantee a [[monifu.reactive.observers.SynchronousObserver SynchronousObserver]]
   * is used, then no buffering is needed and thus the implementation is very efficient.
   *
   * To create an instance, [[SynchronousObserverAsSubscriber.apply]] must be used: {{{
   *   // uses the default requestCount of 128
   *   val subscriber = SynchronousObserverAsSubscriber(new Observer[Int] {
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
   * @param observer the observer instance that will get wrapped into a
   *                 [[Subscriber Subscriber]]
   *
   * @param requestCount the parameter passed to [[Subscription.request]]
   *
   * @param ec the execution context needed for processing asynchronous `Future` results
   */
  def apply[T](observer: SynchronousObserver[T], requestCount: Int = 128)(implicit ec: ExecutionContext): Subscriber[T] = {
    new SynchronousObserverAsSubscriber[T](observer, requestCount)
  }
}