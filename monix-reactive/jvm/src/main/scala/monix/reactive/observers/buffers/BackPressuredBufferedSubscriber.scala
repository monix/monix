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
  * [[monix.reactive.OverflowStrategy.BackPressure BackPressure]]
  * buffer overflow strategy.
  */
private[observers] final class BackPressuredBufferedSubscriber[A] private
  (out: Subscriber[A], _bufferSize: Int)
  extends AbstractBackPressuredBufferedSubscriber[A,A](out, _bufferSize) {

  @volatile protected var p50, p51, p52, p53, p54, p55, p56, p57 = 5
  @volatile protected var q50, q51, q52, q53, q54, q55, q56, q57 = 5

  override protected def fetchNext(): A =
    queue.poll()

  override protected def fetchSize(r: A): Int =
    if (r == null) 0 else 1
}

private[observers] object BackPressuredBufferedSubscriber {
  def apply[A](underlying: Subscriber[A], bufferSize: Int): BackPressuredBufferedSubscriber[A] =
    new BackPressuredBufferedSubscriber[A](underlying, bufferSize)
}