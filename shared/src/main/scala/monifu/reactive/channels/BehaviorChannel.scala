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
 
package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.BufferPolicy.Unbounded
import monifu.reactive.observers.BufferedSubscriber
import monifu.reactive.subjects.BehaviorSubject
import monifu.reactive._

/**
 * A `BehaviorChannel` is a [[Channel]] that uses an underlying
 * [[monifu.reactive.subjects.BehaviorSubject BehaviorSubject]].
 */
final class BehaviorChannel[T] private (initialValue: T, policy: BufferPolicy.Synchronous[T])
    (implicit val scheduler: Scheduler)
  extends Channel[T] with Observable[T] {

  private[this] val lock = new AnyRef
  private[this] val subject = BehaviorSubject(initialValue)
  private[this] val channel = BufferedSubscriber(subject, policy)

  private[this] var isDone = false
  private[this] var lastValue = initialValue
  private[this] var errorThrown = null : Throwable

  def subscribeFn(subscriber: Subscriber[T]): Unit = {
    subject.unsafeSubscribe(subscriber)
  }

  def pushNext(elems: T*): Unit = lock.synchronized {
    if (!isDone)
      for (elem <- elems) {
        lastValue = elem
        channel.observer.onNext(elem)
      }
  }

  def pushComplete() = lock.synchronized {
    if (!isDone) {
      isDone = true
      channel.observer.onComplete()
    }
  }

  def pushError(ex: Throwable) = lock.synchronized {
    if (!isDone) {
      isDone = true
      errorThrown = ex
      channel.observer.onError(ex)
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
  /** Builder for [[monifu.reactive.channels.BehaviorChannel]] */
  def apply[T](initial: T, bufferPolicy: BufferPolicy.Synchronous[T] = Unbounded)
      (implicit s: Scheduler): BehaviorChannel[T] = {

    new BehaviorChannel[T](initial, bufferPolicy)
  }
}
