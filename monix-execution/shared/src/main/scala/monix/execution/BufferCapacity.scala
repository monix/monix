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

package monix.execution

/**
  * Describes the capacity of internal buffers.
  *
  * For abstractions that use an internal buffer, like [[AsyncQueue]],
  * this type provides the info required to build the internal buffer.
  */
sealed abstract class BufferCapacity extends Product with Serializable {
  def isBounded: Boolean
}

object BufferCapacity {
  /**
    * Describes a buffer with a limited capacity.
    *
    * The overflow strategy depends on the implementation, the `capacity`
    * parameter describing just the size of the memory used.
    *
    * Also note that depending on the implementation this capacity can get
    * rounded to a power of 2 for optimization purposes, so it's not
    * necessarily a precise measurement of how many elements can be stored.
    */
  final case class Bounded(capacity: Int) extends BufferCapacity {
    def isBounded = true
  }

  /**
    * Describes an unbounded buffer that can use the entire memory available.
    *
    * @param chunkSizeHint is an optimization hint — in case the underlying
    *        buffer is based on `Array` chunks, the `chunkSizeHint` specifies
    *        the desired chunk size; this parameter is just a hint and
    *        implementations don't guarantee its usage
    */
  final case class Unbounded(chunkSizeHint: Option[Int] = None) extends BufferCapacity {
    def isBounded = false
  }
}
