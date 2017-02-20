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

import monix.execution.exceptions.APIContractViolationException
import scala.runtime.AbstractFunction1

@deprecated("Use monix.execution.exceptions.APIContractViolationException", since="2.2.2")
class MultipleSubscribersException(val observableType: String)
  extends APIContractViolationException(s"$observableType does not support multiple subscribers") 
  with Serializable with Product {

  // Provided for binary backwards compatibility
  def copy(observableType: String = observableType): MultipleSubscribersException =
    new MultipleSubscribersException(observableType)

  // Provided for binary backwards compatibility
  override def productElement(n: Int): Any = {
    if (n != 0) throw new IndexOutOfBoundsException(n.toString)
    observableType
  }

  // Provided for binary backwards compatibility
  override def productArity: Int = 1

  // Provided for binary backwards compatibility
  override def canEqual(that: Any): Boolean =
    that.isInstanceOf[MultipleSubscribersException]
}

object MultipleSubscribersException
  extends AbstractFunction1[String, MultipleSubscribersException] {

  /** For maintaining backwards compatibility. */
  private[reactive] def build(observableType: String): APIContractViolationException =
    new MultipleSubscribersException(observableType)

  /** Builder for [[MultipleSubscribersException]]. */
  @deprecated("Moved to monix.execution.MultipleSubscribersException", "2.2.2")
  def apply(observableType: String): MultipleSubscribersException =
    new MultipleSubscribersException(observableType)

  /** For pattern matching [[MultipleSubscribersException]] instances. */
  @deprecated("Moved to monix.execution.MultipleSubscribersException", "2.2.2")
  def unapply(ex: MultipleSubscribersException): Option[String] =
    Some(ex.observableType)
}