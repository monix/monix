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

import java.util.concurrent.ConcurrentLinkedQueue
import monix.execution.{ BufferCapacity, ChannelType }
import monix.execution.ChannelType.{ MPMC, MPSC, SPMC, SPSC }
import monix.execution.internal.Platform
import monix.execution.internal.collection.LowLevelConcurrentQueue
import monix.execution.internal.jctools.queues._

private[internal] trait LowLevelConcurrentQueueBuilders {
  /**
    * Builds a concurrent queue.
    */
  def apply[A](capacity: BufferCapacity, channelType: ChannelType, fenced: Boolean): LowLevelConcurrentQueue[A] =
    capacity match {
      case BufferCapacity.Bounded(c) => bounded(c, channelType, fenced)
      case BufferCapacity.Unbounded(hint) => unbounded(hint, channelType, fenced)
    }

  /**
    * Builds a bounded `ConcurrentQueue` reference.
    */
  private def bounded[A](capacity: Int, ct: ChannelType, fenced: Boolean): LowLevelConcurrentQueue[A] = {
    val _ = fenced
    ct match {
      case MPMC => FromCircularQueue[A](new MpmcArrayQueue[A](capacity), ct)
      case MPSC => FromCircularQueue[A](new MpscArrayQueue[A](capacity), ct)
      case SPMC => FromCircularQueue[A](new SpmcArrayQueue[A](capacity), ct)
      case SPSC => FromCircularQueue[A](new SpscArrayQueue[A](capacity), ct)
    }
  }

  /**
    * Builds an bounded `ConcurrentQueue` reference.
    */
  private def unbounded[A](chunkSize: Option[Int], ct: ChannelType, fenced: Boolean): LowLevelConcurrentQueue[A] = {
    val _ = fenced
    val chunk = chunkSize.getOrElse(Platform.recommendedBufferChunkSize)

    ct match {
      case MPSC => FromMessagePassingQueue[A](new MpscUnboundedArrayQueue(chunk), ct)
      case SPSC => FromMessagePassingQueue[A](new SpscUnboundedArrayQueue(chunk), ct)
      case _ => new FromJavaQueue[A](new ConcurrentLinkedQueue[A]())
    }
  }
}
