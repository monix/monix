/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.execution.misc

/** Extractor of non-fatal `Throwable` instances.
  *
  * This is an alternative to `scala.util.control.NonFatal`
  * that only considers `VirtualMachineError` as being non-fatal.
  */
object NonFatal {
  /** Returns true if the provided `Throwable` is to be considered non-fatal,
    * or false if it is to be considered fatal
    */
  def apply(t: Throwable): Boolean = t match {
    // VirtualMachineError includes OutOfMemoryError and StackOverflowError
    case _: VirtualMachineError => false
    case _ => true
  }

  /**
    * Returns Some(t) if NonFatal(t) == true, otherwise None
    */
  def unapply(t: Throwable): Option[Throwable] =
    if (apply(t)) Some(t) else None
}
