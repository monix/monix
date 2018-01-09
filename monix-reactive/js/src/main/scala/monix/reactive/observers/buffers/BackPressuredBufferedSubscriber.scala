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

import monix.reactive.observers.Subscriber

/** A `BufferedSubscriber` implementation for the
  * [[monix.reactive.OverflowStrategy.BackPressure BackPressured]]
  * buffer overflowStrategy.
  */
private[monix] final class BackPressuredBufferedSubscriber[A] private
  (out: Subscriber[A], _size: Int)
  extends AbstractBackPressuredBufferedSubscriber[A,A](out,_size) { self =>

  require(_size > 0, "bufferSize must be a strictly positive number")

  override protected def fetchNext(): A =
    queue.poll()
}

private[monix] object BackPressuredBufferedSubscriber {
  /** Builder for [[BackPressuredBufferedSubscriber]] */
  def apply[A](underlying: Subscriber[A], bufferSize: Int): BackPressuredBufferedSubscriber[A] =
    new BackPressuredBufferedSubscriber[A](underlying, bufferSize)
}
