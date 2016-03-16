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

package monix.types

import scala.language.{higherKinds, implicitConversions}

/** Type-class for monadic data-structures that can expose
  * multiple `A` elements.
  */
trait MonadConsError[F[_],E] extends MonadCons[F] with Recoverable[F,E] {
  /** A variant of [[concatMap]] that delays any triggered errors
    * for as long as possible.
    *
    * Typically this means delaying any errors until the source
    * and any child produced by the given function are complete.
    * Since this can involve delaying multiple errors, it is
    * recommended for the final error to be a composite.
    */
  def concatMapDelayError[A,B](fa: F[A])(f: A => F[B]): F[B]

  /** A variant of [[concat]] that delays any triggered errors
    * for as long as possible.
    *
    * Typically this means delaying any errors until the source
    * and any child produced by it are complete. Since this can
    * involve delaying multiple errors, it is recommended for
    * the final error to be a composite.
    */
  def concatDelayError[A](ffa: F[F[A]]): F[A] =
    concatMapDelayError(ffa)(identity)

  /** Mirrors the source, but ends it in error. */
  def endWithError[A](fa: F[A], error: E): F[A] =
    followWith(fa, raiseError(error))
}
