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

import monix.reactive.observers.Subscriber

/** A `BufferedSubscriber` implementation for the
  * [[monix.reactive.OverflowStrategy.BackPressure BackPressure]]
  * buffer overflow strategy.
  */
private[buffers] final class BackPressuredBufferedSubscriber[A] private
  (out: Subscriber[A], bufferSize: Int)
  extends AbstractBackPressuredBufferedSubscriber[A,A](out, bufferSize) {

  override protected def fetchNext(): A = {
    val ref = primaryQueue.relaxedPoll()
    if (ref != null) ref else
      secondaryQueue.poll()
  }

  override protected def fetchSize(r: A): Int =
    if (r == null) 0 else 1
}

private[buffers] object BackPressuredBufferedSubscriber {
  def apply[A](underlying: Subscriber[A], bufferSize: Int): BackPressuredBufferedSubscriber[A] =
    new BackPressuredBufferedSubscriber[A](underlying, bufferSize)
}