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

package monix.execution.misc

import monix.execution.atomic.AtomicAny

private[execution] trait LocalDeprecated[A] { self: Local[A] =>
  /**
    * DEPRECATED — switch to `local.bind[R: CanIsolate]`.
    */
  @deprecated("Switch to local.bind[R: CanIsolate]", since = "3.0.0")
  private[misc] def bind[R](value: A)(f: => R): R = {
    // $COVERAGE-OFF$
    CanBindLocals.synchronous[R].bindKey(self, Some(value))(f)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — switch to `local.bindClear[R: CanIsolate]`.
    */
  @deprecated("Switch to local.bindClear[R: CanIsolate]", since = "3.0.0")
  private[misc] def bindClear[R](f: => R): R = {
    // $COVERAGE-OFF$
    val parent = Local.getContext()
    CanBindLocals.synchronous[R].bindContext(parent.bind(key, None))(f)
    // $COVERAGE-ON$
  }
}

private[execution] trait LocalCompanionDeprecated { self: Local.type =>
  /**
    * DEPRECATED — switch to [[Local.newContext]].
    */
  @deprecated("Renamed to Local.newContext", "3.0.0")
  def defaultContext(): Local.Unbound = {
    // $COVERAGE-OFF$
    new Unbound(AtomicAny(Map.empty))
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — switch to `local.closed[R: CanIsolate]`.
    */
  @deprecated("Switch to local.closed[R: CanIsolate]", since = "3.0.0")
  def closed[R](fn: () => R): () => R = {
    // $COVERAGE-OFF$
    import CanBindLocals.Implicits.synchronousAsDefault
    Local.closed(fn)(implicitly[CanBindLocals[R]])
    // $COVERAGE-ON$
  }
}
