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

/** Exception thrown whenever an execution attempt was rejected.
  *
  * Such execution attempts can come for example from `Task`
  * or from methods returning `Future` references, with this
  * exception being thrown in case the execution was rejected
  * due to in place protections, such as a circuit breaker.
  */
class ExecutionRejectedException(val message: String, cause: Throwable)
  extends RuntimeException(message, cause) with Serializable {

  def this(message: String) = this(message, null)
  def this(cause: Throwable) = this(null, cause)
}

object ExecutionRejectedException
  extends AbstractFunction1[String, ExecutionRejectedException] {

  /** Builder for [[ExecutionRejectedException]]. */
  def apply(message: String): ExecutionRejectedException =
    new ExecutionRejectedException(message)
}
  
