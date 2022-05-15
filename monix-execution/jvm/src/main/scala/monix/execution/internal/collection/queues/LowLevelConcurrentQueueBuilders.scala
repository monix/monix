/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.execution.internal.atomic.UnsafeAccess
import monix.execution.internal.collection.LowLevelConcurrentQueue
import monix.execution.internal.jctools.queues._
import monix.execution.internal.jctools.queues.atomic._

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
  private def bounded[A](capacity: Int, ct: ChannelType, fenced: Boolean): LowLevelConcurrentQueue[A] =
    if (UnsafeAccess.IS_OPENJDK_COMPATIBLE) {
      // Support for memory fences in Unsafe is only available in Java 8+
      if (UnsafeAccess.HAS_JAVA8_INTRINSICS || !fenced)
        ct match {
          case MPMC => FromCircularQueue[A](new MpmcArrayQueue[A](capacity), ct)
          case MPSC => FromCircularQueue[A](new MpscArrayQueue[A](capacity), ct)
          case SPMC => FromCircularQueue[A](new SpmcArrayQueue[A](capacity), ct)
          case SPSC => FromCircularQueue[A](new SpscArrayQueue[A](capacity), ct)
        }
      else {
        // Without support for Unsafe.fullFence, falling back to a MPMC queue
        FromCircularQueue[A](new MpmcArrayQueue[A](capacity), ct)
      }
    } else if (UnsafeAccess.HAS_JAVA8_INTRINSICS || !fenced) {
      ct match {
        case MPMC => FromMessagePassingQueue[A](new MpmcAtomicArrayQueue[A](capacity), ct)
        case MPSC => FromMessagePassingQueue[A](new MpscAtomicArrayQueue[A](capacity), ct)
        case SPMC => FromMessagePassingQueue[A](new SpmcAtomicArrayQueue[A](capacity), ct)
        case SPSC => FromMessagePassingQueue[A](new SpscAtomicArrayQueue[A](capacity), ct)
      }
    } else {
      // Without support for Unsafe.fullFence, falling back to a MPMC queue
      FromMessagePassingQueue[A](new MpmcAtomicArrayQueue[A](capacity), ct)
    }

  /**
    * Builds an bounded `ConcurrentQueue` reference.
    */
  private def unbounded[A](chunkSize: Option[Int], ct: ChannelType, fenced: Boolean): LowLevelConcurrentQueue[A] = {
    val chunk = chunkSize.getOrElse(Platform.recommendedBufferChunkSize)

    if (UnsafeAccess.IS_OPENJDK_COMPATIBLE) {
      // Support for memory fences in Unsafe is only available in Java 8+
      if (UnsafeAccess.HAS_JAVA8_INTRINSICS || !fenced) {
        ct match {
          case MPSC => FromMessagePassingQueue[A](new MpscUnboundedArrayQueue(chunk), ct)
          case SPSC => FromMessagePassingQueue[A](new SpscUnboundedArrayQueue(chunk), ct)
          case _ => new FromJavaQueue[A](new ConcurrentLinkedQueue[A]())
        }
      } else {
        // Without support for Unsafe.fullFence, falling back to a MPMC queue
        new FromJavaQueue[A](new ConcurrentLinkedQueue[A]())
      }
    } else if (UnsafeAccess.HAS_JAVA8_INTRINSICS || !fenced) {
      ct match {
        case MPSC => FromMessagePassingQueue[A](new MpscUnboundedAtomicArrayQueue(chunk), ct)
        case SPSC => FromMessagePassingQueue[A](new SpscUnboundedAtomicArrayQueue(chunk), ct)
        case _ => new FromJavaQueue[A](new ConcurrentLinkedQueue[A]())
      }
    } else {
      // Without support for Unsafe.fullFence, falling back to a MPMC queue
      new FromJavaQueue[A](new ConcurrentLinkedQueue[A]())
    }
  }
}
