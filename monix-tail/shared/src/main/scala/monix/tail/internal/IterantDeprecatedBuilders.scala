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

package monix.tail
package internal
package deprecated

import cats.Applicative

private[tail] trait IterantDeprecatedBuilders[F[_]] extends Any {
  self: IterantBuilders.Apply[F] =>

  /** Binary-compatibility shim — the `Applicative[F]` constraint is no longer needed.
      *
      * In 3.4.0, `suspend(rest)` required an implicit `Applicative[F]`; in 3.5.0 it does not.
      * This overload preserves the old JVM bytecode descriptor so that pre-compiled
      * 3.4.0 call-sites remain link-compatible at runtime.
      *
      * @deprecated Use Iterant.suspend without the implicit instead.
      */
  @deprecated("The Applicative[F] constraint is no longer needed; use suspend without it.", "3.5.0")
  private[deprecated] def suspend[A](rest: F[Iterant[F, A]])(implicit F: Applicative[F]): Iterant[F, A] = {
    // $COVERAGE-OFF$
    Iterant.suspend(rest)
    // $COVERAGE-ON$
  }
}
