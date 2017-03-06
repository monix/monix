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

package monix.reactive.exceptions

/** An exception emitted on buffer overflow, like when
  * using [[monix.reactive.OverflowStrategy.Fail OverflowStrategy.Fail]].
  */
@deprecated("Moved to monix.execution.exceptions.BufferOverflowException", "2.2.2")
class BufferOverflowException(msg: String)
  extends monix.execution.exceptions.BufferOverflowException(msg) {

  // Overrides string because we don't want to show the
  // name of a deprecated exception class
  override def toString: String = {
    val s = classOf[monix.execution.exceptions.BufferOverflowException].getName
    val message: String = getLocalizedMessage
    if (message != null) s + ": " + message
    else s
  }
}

private[reactive] object BufferOverflowException {
  /** Provided for backwards compatibility. */
  def build(msg: String): monix.execution.exceptions.BufferOverflowException =
    new BufferOverflowException(msg)
}