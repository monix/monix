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

import scala.runtime.AbstractFunction1

/** A composite exception represents a list of exceptions
  * that were caught while delaying errors in the processing
  * of observables.
  *
  * Used in operators such as `mergeDelayErrors`,
  * `concatDelayError`, `combineLatestDelayError`, etc...
  */
@deprecated("Moved to monix.execution.CompositeException", "2.2.2")
class CompositeException(errors: Seq[Throwable])
  extends monix.execution.exceptions.CompositeException(errors) with Product {

  // Overrides string because we don't want to show the
  // name of a deprecated exception class
  override def toString: String = {
    val s = classOf[monix.execution.exceptions.CompositeException].getName
    val message: String = getLocalizedMessage
    if (message != null) s + ": " + message
    else s
  }

  // Provided for binary backwards compatibility
  def copy(errors: Seq[Throwable] = errors): CompositeException =
    new CompositeException(errors)

  // Provided for binary backwards compatibility
  override def productArity: Int = 1

  // Provided for binary backwards compatibility
  override def productElement(n: Int): Any = {
    if (n != 0) throw new IndexOutOfBoundsException(n.toString)
    errors
  }

  // Provided for binary backwards compatibility
  override def canEqual(that: Any): Boolean =
    that.isInstanceOf[CompositeException]
}

object CompositeException extends AbstractFunction1[Seq[Throwable], CompositeException] {
  /** Provided for backwards compatibility. */
  private[reactive] def build(errors: Seq[Throwable]): monix.execution.exceptions.CompositeException =
    new CompositeException(errors)

  /** Builder for [[CompositeException]]. */
  @deprecated("Moved to monix.execution.CompositeException", "2.2.2")
  def apply(errors: Seq[Throwable]): CompositeException =
    new CompositeException(errors)

  /** For pattern matching [[CompositeException]] references. */
  @deprecated("Moved to monix.execution.CompositeException", "2.2.2")
  def unapply(ref: CompositeException): Option[Seq[Throwable]] =
    Some(ref.errors)
}