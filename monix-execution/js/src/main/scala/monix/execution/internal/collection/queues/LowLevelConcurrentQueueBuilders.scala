/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution.internal.collection.queues

import monix.execution.{ BufferCapacity, ChannelType }
import monix.execution.internal.collection.{ JSArrayQueue, LowLevelConcurrentQueue }
import scala.annotation.unused

private[internal] trait LowLevelConcurrentQueueBuilders {
  /**
    * Builds a `ConcurrentQueue` reference.
    */
  def apply[A](
    capacity: BufferCapacity,
    @unused channelType: ChannelType,
    @unused fenced: Boolean
  ): LowLevelConcurrentQueue[A] =
    capacity match {
      case BufferCapacity.Bounded(c) => JSArrayQueue.bounded[A](c)
      case BufferCapacity.Unbounded(_) => JSArrayQueue.unbounded[A]
    }
}
