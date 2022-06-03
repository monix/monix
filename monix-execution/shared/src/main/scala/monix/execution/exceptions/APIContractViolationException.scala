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

package monix.execution.exceptions

import scala.runtime.AbstractFunction1
import scala.util.{ Failure, Success, Try }

/** Generic exception thrown on API contract violations. */
class APIContractViolationException(val message: String, cause: Throwable)
  extends IllegalStateException(message, cause) with Serializable {

  def this(message: String) = this(message, null)
  def this(cause: Throwable) = this(null, cause)
}

object APIContractViolationException extends AbstractFunction1[String, APIContractViolationException] {
  /** Builder for [[APIContractViolationException]]. */
  def apply(message: String): APIContractViolationException =
    new APIContractViolationException(message)

  def unapply(arg: APIContractViolationException): Option[(String, Throwable)] =
    Some((arg.message, arg.getCause))
}

/**
  * Thrown when signaling is attempted multiple times for
  * [[monix.execution.Callback Callback]] or similar.
  */
class CallbackCalledMultipleTimesException(message: String, cause: Throwable)
  extends APIContractViolationException(message, cause) {

  def this(message: String) = this(message, null)
  def this(cause: Throwable) = this(null, cause)
}

object CallbackCalledMultipleTimesException extends AbstractFunction1[String, CallbackCalledMultipleTimesException] {
  /** Builder for [[APIContractViolationException]]. */
  def apply(message: String): CallbackCalledMultipleTimesException =
    new CallbackCalledMultipleTimesException(message)

  def unapply(arg: CallbackCalledMultipleTimesException): Option[(String, Throwable)] =
    Some((arg.message, arg.getCause))

  def forResult[E](r: Try[_]): CallbackCalledMultipleTimesException =
    forResult(r match { case Success(a) => Right(a); case Failure(e) => Left(e) })

  def forResult[E](r: Either[E, _]): CallbackCalledMultipleTimesException = {
    val (msg, cause) = r match {
      case Left(e) => ("onError", UncaughtErrorException.wrap(e))
      case Right(_) => ("onSuccess", null)
    }
    new CallbackCalledMultipleTimesException(msg, cause)
  }
}
