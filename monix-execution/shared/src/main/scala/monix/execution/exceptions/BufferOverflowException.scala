/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.execution.exceptions

import scala.runtime.AbstractFunction1

/** An exception emitted on buffer overflows, like when using
  * [[monix.reactive.OverflowStrategy.Fail OverflowStrategy.Fail]].
  */
class BufferOverflowException(val message: String) extends RuntimeException(message) with Serializable

object BufferOverflowException extends AbstractFunction1[String, BufferOverflowException] {
  /** Builder for [[BufferOverflowException]]. */
  def apply(message: String): BufferOverflowException =
    new BufferOverflowException(message)
}
