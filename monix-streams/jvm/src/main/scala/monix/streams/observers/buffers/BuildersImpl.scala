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

import monix.streams.OverflowStrategy
import monix.streams.OverflowStrategy._
import monix.streams.observers.{Subscriber, BufferedSubscriber, SyncSubscriber}

private[observers] trait BuildersImpl {  self: BufferedSubscriber.type =>
  def apply[A](subscriber: Subscriber[A], bufferPolicy: OverflowStrategy[A]): Subscriber[A] = {
    bufferPolicy match {
      case Unbounded =>
        SimpleBufferedSubscriber.unbounded(subscriber)
      case Fail(bufferSize) =>
        SimpleBufferedSubscriber.overflowTriggering(subscriber, bufferSize)
      case BackPressure(bufferSize) =>
        BackPressuredBufferedSubscriber(subscriber, bufferSize)

      case DropNew(bufferSize) =>
        DropNewBufferedSubscriber.simple(subscriber, bufferSize)
      case DropNewAndSignal(bufferSize, f) =>
        DropNewBufferedSubscriber.withSignal(subscriber, bufferSize, f)

      case DropOld(bufferSize) =>
        EvictingBufferedSubscriber.dropOld(subscriber, bufferSize)
      case DropOldAndSignal(bufferSize, f) =>
        EvictingBufferedSubscriber.dropOldAndSignal[A](subscriber, bufferSize, f)

      case ClearBuffer(bufferSize) =>
        EvictingBufferedSubscriber.clearBuffer(subscriber, bufferSize)
      case ClearBufferAndSignal(bufferSize, f) =>
        EvictingBufferedSubscriber.clearBufferAndSignal(subscriber, bufferSize, f)
    }
  }

  def synchronous[A](subscriber: Subscriber[A], bufferPolicy: OverflowStrategy.Synchronous[A]): SyncSubscriber[A] = {
    bufferPolicy match {
      case Unbounded =>
        SimpleBufferedSubscriber.unbounded(subscriber)
      case Fail(bufferSize) =>
        SimpleBufferedSubscriber.overflowTriggering(subscriber, bufferSize)

      case DropNew(bufferSize) =>
        DropNewBufferedSubscriber.simple(subscriber, bufferSize)
      case DropNewAndSignal(bufferSize, f) =>
        DropNewBufferedSubscriber.withSignal(subscriber, bufferSize, f)

      case DropOld(bufferSize) =>
        EvictingBufferedSubscriber.dropOld(subscriber, bufferSize)
      case DropOldAndSignal(bufferSize, f) =>
        EvictingBufferedSubscriber.dropOldAndSignal(subscriber, bufferSize, f)

      case ClearBuffer(bufferSize) =>
        EvictingBufferedSubscriber.clearBuffer(subscriber, bufferSize)
      case ClearBufferAndSignal(bufferSize, f) =>
        EvictingBufferedSubscriber.clearBufferAndSignal(subscriber, bufferSize, f)
    }
  }
}
