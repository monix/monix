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

import scala.concurrent.duration.FiniteDuration
import scala.runtime.AbstractFunction1

/** Moved to [[monix.execution.exceptions.DownstreamTimeoutException]]. */
@deprecated("Moved to monix.execution.exceptions.DownstreamTimeoutException", "2.2.2")
class DownstreamTimeoutException(timeout: FiniteDuration)
  extends monix.execution.exceptions.DownstreamTimeoutException(timeout)
  with Product {

  // Overrides string because we don't want to show the
  // name of a deprecated exception class
  override def toString: String = {
    val s = classOf[monix.execution.exceptions.DownstreamTimeoutException].getName
    val message: String = getLocalizedMessage
    if (message != null) s + ": " + message
    else s
  }

  // Provided for binary backwards compatibility
  def copy(timeout: FiniteDuration = timeout): DownstreamTimeoutException =
    new DownstreamTimeoutException(timeout)

  // Provided for binary backwards compatibility
  override def productElement(n: Int): Any = {
    if (n != 0) throw new IndexOutOfBoundsException(n.toString)
    timeout
  }

  // Provided for binary backwards compatibility
  override def productArity: Int = 1

  // Provided for binary backwards compatibility
  override def canEqual(that: Any): Boolean =
    that.isInstanceOf[DownstreamTimeoutException]
}

/** Moved to [[monix.execution.exceptions.DownstreamTimeoutException]]. */
object DownstreamTimeoutException
  extends AbstractFunction1[FiniteDuration, DownstreamTimeoutException] {

  /** Provided for backwards compatibility. */
  private[reactive] def build(timeout: FiniteDuration): monix.execution.exceptions.DownstreamTimeoutException =
    new DownstreamTimeoutException(timeout)

  /** Builder for [[DownstreamTimeoutException]]. */
  @deprecated("Moved to monix.execution.exceptions.DownstreamTimeoutException", "2.2.2")
  def apply(timeout: FiniteDuration): DownstreamTimeoutException =
    new DownstreamTimeoutException(timeout)

  /** For pattern matching [[DownstreamTimeoutException]] instances. */
  @deprecated("Moved to monix.execution.exceptions.DownstreamTimeoutException", "2.2.2")
  def unapply(ex: DownstreamTimeoutException): Option[FiniteDuration] =
    Some(ex.timeout)
}