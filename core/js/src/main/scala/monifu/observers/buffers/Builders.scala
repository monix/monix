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

package monifu.observers.buffers

import monifu.OverflowStrategy._
import monifu.observers.{BufferedSubscriber, SynchronousSubscriber}
import monifu.{OverflowStrategy, Subscriber}

trait Builders { self: BufferedSubscriber.type =>
  def apply[T](subscriber: Subscriber[T], bufferPolicy: OverflowStrategy): Subscriber[T] = {
    bufferPolicy match {
      case Unbounded =>
        SynchronousBufferedSubscriber.unbounded(subscriber)
      case Fail(bufferSize) =>
        SynchronousBufferedSubscriber.bounded(subscriber, bufferSize)
      case BackPressure(bufferSize) =>
        BackPressuredBufferedSubscriber(subscriber, bufferSize)
      case DropNew(bufferSize) =>
        SynchronousBufferedSubscriber.dropNew(subscriber, bufferSize)
      case DropOld(bufferSize) =>
        SynchronousBufferedSubscriber.dropOld(subscriber, bufferSize)
      case ClearBuffer(bufferSize) =>
        SynchronousBufferedSubscriber.clearBuffer(subscriber, bufferSize)
    }
  }

  def synchronous[T](subscriber: Subscriber[T], bufferPolicy: OverflowStrategy.Synchronous): SynchronousSubscriber[T] = {
    bufferPolicy match {
      case Unbounded =>
        SynchronousBufferedSubscriber.unbounded(subscriber)
      case Fail(bufferSize) =>
        SynchronousBufferedSubscriber.bounded(subscriber, bufferSize)
      case DropNew(bufferSize) =>
        SynchronousBufferedSubscriber.dropNew(subscriber, bufferSize)
      case DropOld(bufferSize) =>
        SynchronousBufferedSubscriber.dropOld(subscriber, bufferSize)
      case ClearBuffer(bufferSize) =>
        SynchronousBufferedSubscriber.clearBuffer(subscriber, bufferSize)
    }
  }

  private[monifu] def apply[T](subscriber: Subscriber[T], strategy: OverflowStrategy, onOverflow: Long => T): Subscriber[T] = {
    strategy match {
      case withSignal: Evicted if onOverflow != null =>
        withOverflowSignal(subscriber, withSignal)(onOverflow)
      case _ =>
        apply(subscriber, strategy)
    }
  }

  def withOverflowSignal[T](subscriber: Subscriber[T], overflowStrategy: OverflowStrategy.Evicted)
    (onOverflow: Long => T): SynchronousSubscriber[T] = {

    overflowStrategy match {
      case DropNew(bufferSize) =>
        SynchronousBufferedSubscriber.dropNew(subscriber, bufferSize, onOverflow)

      case DropOld(bufferSize) =>
        SynchronousBufferedSubscriber.dropOld(subscriber, bufferSize, onOverflow)

      case ClearBuffer(bufferSize) =>
        SynchronousBufferedSubscriber.clearBuffer(subscriber, bufferSize, onOverflow)
    }
  }
}
