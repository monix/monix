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

import java.lang.invoke.VarHandle
import monix.execution.ChannelType
import monix.execution.internal.collection.LowLevelConcurrentQueue
import monix.execution.internal.jctools.queues.MessagePassingQueue
import scala.collection.mutable

private[internal] abstract class FromMessagePassingQueue[A](queue: MessagePassingQueue[A])
  extends LowLevelConcurrentQueue[A] {

  def fenceOffer(): Unit
  def fencePoll(): Unit

  final def isEmpty: Boolean =
    queue.isEmpty
  final def offer(elem: A): Int =
    if (queue.offer(elem)) 0 else 1
  final def poll(): A =
    queue.poll()
  final def clear(): Unit =
    queue.clear()

  final def drainToBuffer(buffer: mutable.Buffer[A], limit: Int): Int = {
    val consume = new QueueDrain[A](buffer)
    queue.drain(consume, limit)
    consume.count
  }
}

private[internal] object FromMessagePassingQueue {
  /**
    * Builds a [[FromMessagePassingQueue]] instance.
    */
  def apply[A](queue: MessagePassingQueue[A], ct: ChannelType): FromMessagePassingQueue[A] =
    ct match {
      case ChannelType.MPMC =>
        new MPMC[A](queue)
      case ChannelType.MPSC =>
        new Java8MPSC[A](queue)
      case ChannelType.SPMC =>
        new Java8SPMC[A](queue)
      case ChannelType.SPSC =>
        new Java8SPSC[A](queue)
    }

  private final class MPMC[A](queue: MessagePassingQueue[A]) extends FromMessagePassingQueue[A](queue) {

    def fenceOffer(): Unit = ()
    def fencePoll(): Unit = ()
  }

  private final class Java8SPMC[A](queue: MessagePassingQueue[A]) extends FromMessagePassingQueue[A](queue) {

    def fenceOffer(): Unit = VarHandle.fullFence()
    def fencePoll(): Unit = ()
  }

  private final class Java8MPSC[A](queue: MessagePassingQueue[A]) extends FromMessagePassingQueue[A](queue) {

    def fenceOffer(): Unit = ()
    def fencePoll(): Unit = VarHandle.fullFence()
  }

  private final class Java8SPSC[A](queue: MessagePassingQueue[A]) extends FromMessagePassingQueue[A](queue) {

    def fenceOffer(): Unit = VarHandle.fullFence()
    def fencePoll(): Unit = VarHandle.fullFence()
  }
}
