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
 *
 */
 
package monix.streams.channels

import monix.execution.Scheduler
import monix.streams.observers.BufferedSubscriber
import monix.streams.{Subscriber, OverflowStrategy, Channel}
import monix.streams.subjects.BehaviorSubject

/**
 * A `BehaviorChannel` is a [[Channel]] that uses an underlying
 * [[monix.streams.subjects.BehaviorSubject BehaviorSubject]].
 */
final class BehaviorChannel[T] private 
  (initialValue: T, strategy: OverflowStrategy.Synchronous, onOverflow: Long => T)
  (implicit val scheduler: Scheduler)
  extends ObservableChannel[T,T] {

  private[this] val lock = new AnyRef
  private[this] val subject = BehaviorSubject(initialValue)
  private[this] val channel = BufferedSubscriber(
    Subscriber(subject, scheduler), strategy, onOverflow)

  private[this] var isDone = false
  private[this] var lastValue = initialValue
  private[this] var errorThrown = null : Throwable

  def unsafeSubscribeFn(subscriber: Subscriber[T]): Unit = {
    subject.unsafeSubscribeFn(subscriber)
  }

  def pushNext(elems: T*): Unit = lock.synchronized {
    if (!isDone)
      for (elem <- elems) {
        lastValue = elem
        channel.onNext(elem)
      }
  }

  def pushComplete() = lock.synchronized {
    if (!isDone) {
      isDone = true
      channel.onComplete()
    }
  }

  def pushError(ex: Throwable) = lock.synchronized {
    if (!isDone) {
      isDone = true
      errorThrown = ex
      channel.onError(ex)
    }
  }

  def :=(update: T): Unit = pushNext(update)

  def apply(): T = lock.synchronized {
    if (errorThrown ne null)
      throw errorThrown
    else
      lastValue
  }
}

object BehaviorChannel {
  /**
   * Builds a [[Channel Channel]] that uses an underlying
   * [[monix.streams.subjects.BehaviorSubject BehaviorSubject]].
   *
   * @param strategy - the [[OverflowStrategy overflow strategy]]
   *        used for buffering, which specifies what to do in case
   *        we're dealing with slow consumers: should an unbounded
   *        buffer be used, should back-pressure be applied, should
   *        the pipeline drop newer or older events, should it drop
   *        the whole buffer?  See [[OverflowStrategy]] for more
   *        details.
   */
  def apply[T](initial: T, strategy: OverflowStrategy.Synchronous)
    (implicit s: Scheduler): BehaviorChannel[T] = {

    new BehaviorChannel[T](initial, strategy, null)
  }

  /**
   * Builds a [[Channel Channel]] that uses an underlying
   * [[monix.streams.subjects.BehaviorSubject BehaviorSubject]].
   *
   * @param strategy - the [[OverflowStrategy overflow strategy]]
   *        used for buffering, which specifies what to do in case
   *        we're dealing with slow consumers: should an unbounded
   *        buffer be used, should back-pressure be applied, should
   *        the pipeline drop newer or older events, should it drop
   *        the whole buffer?  See [[OverflowStrategy]] for more
   *        details.
   * @param onOverflow - a function that is used for signaling a special
   *        event used to inform the consumers that an overflow event
   *        happened, function that receives the number of dropped
   *        events as a parameter (see [[OverflowStrategy.Evicted]])
   */
  def apply[T](initial: T, strategy: OverflowStrategy.Evicted, onOverflow: Long => T)
    (implicit s: Scheduler): BehaviorChannel[T] = {

    new BehaviorChannel[T](initial, strategy, onOverflow)
  }
}
