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

package monix.reactive.observers.buffers

import monix.execution.internal.Platform
import monix.reactive.observers.Subscriber
import org.jctools.queues.MessagePassingQueue
import org.jctools.queues.MessagePassingQueue.Consumer
import scala.collection.mutable.ListBuffer

/** A `BufferedSubscriber` implementation for the
  * [[monix.reactive.OverflowStrategy.BackPressure BackPressured]]
  * buffer overflowStrategy that sends events in bundles.
  */
private[monix] final class BatchedBufferedSubscriber[A] private
  (out: Subscriber[List[A]], bufferSize: Int)
  extends AbstractBackPressuredBufferedSubscriber[A, ListBuffer[A]](
    subscriberBufferToList(out), bufferSize) { self =>

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  @volatile protected var p50, p51, p52, p53, p54, p55, p56, p57 = 5
  @volatile protected var q50, q51, q52, q53, q54, q55, q56, q57 = 5

  override protected def fetchSize(r: ListBuffer[A]): Int =
    r.length

  override protected def fetchNext(): ListBuffer[A] = {
    def drainFrom(queue: MessagePassingQueue[A], buffer: ListBuffer[A], limit: Int): Unit = {
      val consumer: Consumer[A] = new Consumer[A] { def accept(e: A): Unit = buffer += e }
      queue.drain(consumer, limit)
    }

    val batchSize = Platform.recommendedBatchSize
    val buffer = ListBuffer.empty[A]
    drainFrom(primaryQueue, buffer, batchSize)

    val drained = buffer.length
    if (drained < batchSize) drainFrom(secondaryQueue, buffer, batchSize - drained)
    if (buffer.nonEmpty) buffer else null
  }
}

private[monix] object BatchedBufferedSubscriber {
  /** Builder for [[BatchedBufferedSubscriber]] */
  def apply[T](underlying: Subscriber[List[T]], bufferSize: Int): BatchedBufferedSubscriber[T] =
    new BatchedBufferedSubscriber[T](underlying, bufferSize)
}