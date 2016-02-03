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

package monix.streams.observers.buffers

import monix.streams.OverflowStrategy._
import monix.streams.{OverflowStrategy, Subscriber}
import monix.streams.observers.{BufferedSubscriber, SyncSubscriber}

trait Builders { self: BufferedSubscriber.type =>
  def apply[T](subscriber: Subscriber[T], bufferPolicy: OverflowStrategy): Subscriber[T] = {
    bufferPolicy match {
      case Unbounded =>
        SyncBufferedSubscriber.unbounded(subscriber)
      case Fail(bufferSize) =>
        SyncBufferedSubscriber.bounded(subscriber, bufferSize)
      case BackPressure(bufferSize) =>
        BackPressuredBufferedSubscriber(subscriber, bufferSize)
      case DropNew(bufferSize) =>
        SyncBufferedSubscriber.dropNew(subscriber, bufferSize)
      case DropOld(bufferSize) =>
        SyncBufferedSubscriber.dropOld(subscriber, bufferSize)
      case ClearBuffer(bufferSize) =>
        SyncBufferedSubscriber.clearBuffer(subscriber, bufferSize)
    }
  }

  def synchronous[T](subscriber: Subscriber[T], bufferPolicy: OverflowStrategy.Synchronous): SyncSubscriber[T] = {
    bufferPolicy match {
      case Unbounded =>
        SyncBufferedSubscriber.unbounded(subscriber)
      case Fail(bufferSize) =>
        SyncBufferedSubscriber.bounded(subscriber, bufferSize)
      case DropNew(bufferSize) =>
        SyncBufferedSubscriber.dropNew(subscriber, bufferSize)
      case DropOld(bufferSize) =>
        SyncBufferedSubscriber.dropOld(subscriber, bufferSize)
      case ClearBuffer(bufferSize) =>
        SyncBufferedSubscriber.clearBuffer(subscriber, bufferSize)
    }
  }

  private[monix] def apply[T](subscriber: Subscriber[T],
    strategy: OverflowStrategy, onOverflow: Long => T): Subscriber[T] = {

    if (strategy.isEvicted)
      withOverflowSignal(subscriber, strategy.asInstanceOf[Evicted])(onOverflow)
    else
      apply(subscriber, strategy)
  }

  private[monix] def synchronous[T](subscriber: Subscriber[T],
    strategy: OverflowStrategy.Synchronous, onOverflow: Long => T): SyncSubscriber[T] = {

    if (strategy.isEvicted)
      withOverflowSignal(subscriber, strategy.asInstanceOf[Evicted])(onOverflow)
    else
      synchronous(subscriber, strategy)
  }

  def withOverflowSignal[T](subscriber: Subscriber[T], overflowStrategy: OverflowStrategy.Evicted)
    (onOverflow: Long => T): SyncSubscriber[T] = {

    overflowStrategy match {
      case DropNew(bufferSize) =>
        SyncBufferedSubscriber.dropNew(subscriber, bufferSize, onOverflow)

      case DropOld(bufferSize) =>
        SyncBufferedSubscriber.dropOld(subscriber, bufferSize, onOverflow)

      case ClearBuffer(bufferSize) =>
        SyncBufferedSubscriber.clearBuffer(subscriber, bufferSize, onOverflow)
    }
  }
}
