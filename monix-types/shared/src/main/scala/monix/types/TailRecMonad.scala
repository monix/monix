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

/** A shim for a `TailRecMonad` type-class, to be supplied
  * by / translated to libraries such as Cats or Scalaz.
  *
  * This type-class representing monads with a tail-recursive
  * flatMap implementation.
  */
trait TailRecMonad[F[_]] extends Monad[F] {
  /** Keeps calling `f` until a `scala.util.Right[B]` is returned.
    *
    * Based on Phil Freeman's
    * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
    *
    * Implementations of this method should use constant stack AND heap space.
    */
  def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] =
    flatMap(f(a)) {
      case Right(b) => pure(b)
      case Left(nextA) => tailRecM(nextA)(f)
    }
}

object TailRecMonad {
  @inline def apply[F[_]](implicit F: TailRecMonad[F]): TailRecMonad[F] = F
}
