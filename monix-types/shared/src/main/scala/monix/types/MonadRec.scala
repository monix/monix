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

/** This type-class represents monads with a tail-recursive
  * `tailRecM` implementation.
  *
  * Based on Phil Freeman's
  * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
  *
  * To implement `MonadRec`:
  *
  *  - inherit from [[MonadRec.Type]] in derived type-classes
  *  - inherit from [[MonadRec.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scado
  * project and [[https://github.com/scalaz/scalaz/ Scalaz 8]] and
  * the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait MonadRec[F[_]] extends Serializable with Monad.Type[F] {
  self: MonadRec.Instance[F] =>

  /** Keeps calling `f` until a `scala.util.Right[B]` is returned. */
  def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] =
    flatMap(f(a)) {
      case Right(b) => pure(b)
      case Left(nextA) => tailRecM(nextA)(f)
    }
}

object MonadRec {
  @inline def apply[F[_]](implicit F: MonadRec[F]): MonadRec[F] = F

  /** The `MonadRec.Type` should be inherited in type-classes that
    * are derived from [[MonadRec]].
    */
  trait Type[F[_]] extends Monad.Type[F] {
    implicit def monadRec: MonadRec[F]
  }

  /** The `MonadRec.Instance` provides the means to combine
    * [[MonadRec]] instances with other type-classes.
    *
    * To be inherited by `MonadRec` instances.
    */
  trait Instance[F[_]] extends MonadRec[F] with Type[F] with Monad.Instance[F] {
    override final def monadRec: MonadRec[F] = this
  }
}
