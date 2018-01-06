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

package monix.reactive.observers.buffers

import monix.execution.internal.Platform
import monix.reactive.observers.Subscriber
import scala.collection.mutable.ListBuffer

/** A `BufferedSubscriber` implementation for the
  * [[monix.reactive.OverflowStrategy.BackPressure BackPressured]]
  * buffer overflowStrategy that sends events in bundles.
  */
private[monix] final class BatchedBufferedSubscriber[A] private
  (out: Subscriber[List[A]], _bufferSize: Int)
  extends AbstractBackPressuredBufferedSubscriber[A, ListBuffer[A]](
    subscriberBufferToList(out), _bufferSize) { self =>

  @volatile protected var p50, p51, p52, p53, p54, p55, p56, p57 = 5
  @volatile protected var q50, q51, q52, q53, q54, q55, q56, q57 = 5

  override protected def fetchSize(r: ListBuffer[A]): Int =
    r.length

  override protected def fetchNext(): ListBuffer[A] = {
    val batchSize = Platform.recommendedBatchSize
    val buffer = ListBuffer.empty[A]
    queue.drain(buffer, batchSize)
    if (buffer.nonEmpty) buffer else null
  }
}

private[monix] object BatchedBufferedSubscriber {
  /** Builder for [[BatchedBufferedSubscriber]] */
  def apply[A](underlying: Subscriber[List[A]], bufferSize: Int): BatchedBufferedSubscriber[A] =
    new BatchedBufferedSubscriber[A](underlying, bufferSize)
}