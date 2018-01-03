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
  (out: Subscriber[List[A]], bufferSize: Int)
  extends AbstractBackPressuredBufferedSubscriber[A, List[A]](out, bufferSize) { self =>

  override protected def fetchNext(): List[A] =
    if (queue.isEmpty) null else {
      val buffer = ListBuffer.empty[A]
      queue.drainToBuffer(buffer, Platform.recommendedBatchSize)
      buffer.toList
    }
}

private[monix] object BatchedBufferedSubscriber {
  /** Builder for [[BatchedBufferedSubscriber]] */
  def apply[A](underlying: Subscriber[List[A]], bufferSize: Int): BatchedBufferedSubscriber[A] =
    new BatchedBufferedSubscriber[A](underlying, bufferSize)
}