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

package monix.execution.exceptions

import scala.runtime.AbstractFunction1

/** A composite exception represents a list of exceptions
  * that were caught while delaying errors.
  */
class CompositeException(val errors: Seq[Throwable])
  extends RuntimeException() with Serializable {

  override def toString: String = {
    getClass.getName + (
      if (errors.isEmpty) "" else {
        val (first, last) = errors.splitAt(2)
        val str = first.map(_.getClass.getName).mkString(", ")
        val reasons = if (last.nonEmpty) str + "..." else str
        "(" + reasons + ")"
      })
  }
}

object CompositeException extends AbstractFunction1[Seq[Throwable], CompositeException] {
  /** Builder for [[CompositeException]]. */
  def apply(errors: Seq[Throwable]): CompositeException =
    new CompositeException(errors)

  /** For pattern matching [[CompositeException]] references. */
  def unapply(ref: CompositeException): Option[Seq[Throwable]] =
    Some(ref.errors)
}