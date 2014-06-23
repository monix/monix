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
 
package monifu.reactive.channels

import monifu.reactive.BufferPolicy.Unbounded
import monifu.reactive.{BufferPolicy, Observable, Observer, Channel}
import monifu.reactive.observers.BufferedObserver
import monifu.concurrent.Scheduler
import monifu.reactive.subjects.BehaviorSubject
import monifu.concurrent.locks.SpinLock

/**
 * A `BehaviorChannel` is a [[Channel]] that uses an underlying
 * [[monifu.reactive.subjects.BehaviorSubject BehaviorSubject]].
 */
final class BehaviorChannel[T] private (initialValue: T, policy: BufferPolicy, s: Scheduler) extends Channel[T] with Observable[T] {
  implicit val scheduler = s

  private[this] val lock = SpinLock()
  private[this] val subject = BehaviorSubject(initialValue)
  private[this] val channel = BufferedObserver(subject, policy)

  private[this] var isDone = false
  private[this] var lastValue = initialValue
  private[this] var errorThrown = null : Throwable

  def subscribeFn(observer: Observer[T]): Unit = {
    subject.subscribeFn(observer)
  }

  def pushNext(elems: T*): Unit = lock.enter {
    if (!isDone)
      for (elem <- elems) {
        lastValue = elem
        channel.onNext(elem)
      }
  }

  def pushComplete() = lock.enter {
    if (!isDone) {
      isDone = true
      channel.onComplete()
    }
  }

  def pushError(ex: Throwable) = lock.enter {
    if (!isDone) {
      isDone = true
      errorThrown = ex
      channel.onError(ex)
    }
  }

  def :=(update: T): Unit = pushNext(update)

  def apply(): T = lock.enter {
    if (errorThrown ne null)
      throw errorThrown
    else
      lastValue
  }
}

object BehaviorChannel {
  def apply[T](initial: T, bufferPolicy: BufferPolicy = Unbounded)(implicit s: Scheduler): BehaviorChannel[T] =
    new BehaviorChannel[T](initial, bufferPolicy, s)
}
