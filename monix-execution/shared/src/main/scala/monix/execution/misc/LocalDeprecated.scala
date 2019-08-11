/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

private[misc] trait LocalDeprecated[A] { self: Local[A] =>

  /** DEPRECATED — switch to `local.bind[R: CanIsolate]`. */
  @deprecated("Switch to local.bind[R: CanIsolate]", since = "3.0.0")
  protected def bind[R](value: A)(f: => R): R = {
    // $COVERAGE-OFF$
    val parent = Local.getContext()
    CanIsolate[R].bind(parent.bind(key, Some(value)))(f)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — switch to `local.bindClear[R: CanIsolate]`. */
  @deprecated("Switch to local.bindClear[R: CanIsolate]", since = "3.0.0")
  protected def bindClear[R](f: => R): R = {
    // $COVERAGE-OFF$
    val parent = Local.getContext()
    CanIsolate[R].bind(parent.bind(key, None))(f)
    // $COVERAGE-ON$
  }

}
