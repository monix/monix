/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.collection.mutable

import scala.collection.mutable

private[monifu] final class UnlimitedBuffer[T] private () extends Buffer[T] {
  private[this] val buffer = mutable.ArrayBuffer.empty[T]

  def offer(elem: T): Int = {
    buffer.append(elem)
    0
  }

  def offerMany(seq: T*): Long = {
    buffer.append(seq: _*)
    0L
  }

  def iterator: Iterator[T] = buffer.iterator
  def apply(idx: Int): T = buffer(idx)
  def clear(): Unit = buffer.clear()
  def length: Int = buffer.length
}

object UnlimitedBuffer {
  def apply[T](): UnlimitedBuffer[T] =
    new UnlimitedBuffer[T]()
}