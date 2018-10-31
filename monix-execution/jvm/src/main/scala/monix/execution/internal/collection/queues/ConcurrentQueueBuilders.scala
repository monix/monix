/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import monix.execution.{BufferCapacity, ChannelType}
import monix.execution.ChannelType.{MPMC, MPSC, SPMC, SPSC}
import monix.execution.internal.Platform
import monix.execution.internal.atomic.UnsafeAccess
import monix.execution.internal.collection.ConcurrentQueue
import org.jctools.queues._
import org.jctools.queues.atomic._

private[internal] trait ConcurrentQueueBuilders {
  /**
    * Builds a concurrent queue.
    */
  def apply[A](capacity: BufferCapacity, channelType: ChannelType): ConcurrentQueue[A] =
    capacity match {
      case BufferCapacity.Bounded(c) => bounded(c, channelType)
      case BufferCapacity.Unbounded(hint) => unbounded(hint, channelType)
    }

  /**
    * Builds a bounded `ConcurrentQueue` reference.
    */
  def bounded[A](capacity: Int, channelType: ChannelType): ConcurrentQueue[A] =
    if (UnsafeAccess.IS_OPENJDK_COMPATIBLE)
      channelType match {
        case MPMC => new FromCircularQueue[A](new MpmcArrayQueue[A](capacity))
        case MPSC => new FromCircularQueue[A](new MpscArrayQueue[A](capacity))
        case SPMC => new FromCircularQueue[A](new SpmcArrayQueue[A](capacity))
        case SPSC => new FromCircularQueue[A](new SpscArrayQueue[A](capacity))
      }
    else
      channelType match {
        case MPMC => new FromMessagePassingQueue[A](new MpmcAtomicArrayQueue[A](capacity))
        case MPSC => new FromMessagePassingQueue[A](new MpscAtomicArrayQueue[A](capacity))
        case SPMC => new FromMessagePassingQueue[A](new SpmcAtomicArrayQueue[A](capacity))
        case SPSC => new FromMessagePassingQueue[A](new SpscAtomicArrayQueue[A](capacity))
      }

  /**
    * Builds an bounded `ConcurrentQueue` reference.
    */
  def unbounded[A](chunkSize: Option[Int], channelType: ChannelType): ConcurrentQueue[A] = {
    val chunk = chunkSize.getOrElse(Platform.recommendedBatchSize)
    if (UnsafeAccess.IS_OPENJDK_COMPATIBLE)
      channelType match {
        case MPSC => new FromMessagePassingQueue[A](new MpscUnboundedArrayQueue(chunk))
        case SPSC => new FromMessagePassingQueue[A](new SpscUnboundedArrayQueue(chunk))
        case _ => new FromJavaQueue[A](new ConcurrentLinkedQueue[A]())
      }
    else
      channelType match {
        case MPSC => new FromMessagePassingQueue[A](new MpscUnboundedAtomicArrayQueue(chunk))
        case SPSC => new FromMessagePassingQueue[A](new SpscUnboundedAtomicArrayQueue(chunk))
        case _ => new FromJavaQueue[A](new ConcurrentLinkedQueue[A]())
      }
  }
}
