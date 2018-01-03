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

import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.runtime.AbstractFunction1

/** Exception thrown whenever a upstream listener on a
  * back-pressured data-source is taking too long to process
  * a received event.
  */
class UpstreamTimeoutException(val timeout: FiniteDuration)
  extends TimeoutException(s"Upstream timeout after $timeout")
    with Serializable

object UpstreamTimeoutException
  extends AbstractFunction1[FiniteDuration, UpstreamTimeoutException] {

  /** Builder for [[UpstreamTimeoutException]]. */
  def apply(timeout: FiniteDuration): UpstreamTimeoutException =
    new UpstreamTimeoutException(timeout)

  /** For pattern matching [[UpstreamTimeoutException]] instances. */
  def unapply(ex: UpstreamTimeoutException): Option[FiniteDuration] =
    Some(ex.timeout)
}