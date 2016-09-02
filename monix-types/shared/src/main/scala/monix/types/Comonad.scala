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

/** The `Comonad` type-class is the dual of [[Monad]]. Whereas Monads
  * allow for the composition of effectful functions, Comonads allow
  * for composition of functions that extract the value from their
  * context.
  *
  * To implement `Comonad`:
  *
  *  - inherit from [[Comonad.Type]] in derived type-classes
  *  - inherit from [[Comonad.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scado
  * project and [[https://github.com/scalaz/scalaz/ Scalaz 8]] and
  * the type has been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Comonad[F[_]] extends Serializable with Cobind.Type[F] {
  self: Comonad.Instance[F] =>

  def extract[A](x: F[A]): A
}

object Comonad {
  @inline def apply[F[_]](implicit F: Comonad[F]): Comonad[F] = F

  /** The `Comonad.Type` should be inherited in type-classes that
    * are derived from [[Comonad]].
    */
  trait Type[F[_]] extends Cobind.Type[F] {
    implicit def comonad: Comonad[F]
  }

  /** The `Comonad.Instance` provides the means to combine
    * [[Comonad]] instances with other type-classes.
    *
    * To be inherited by `Comonad` instances.
    */
  trait Instance[F[_]] extends Comonad[F] with Type[F] with Cobind.Instance[F] {
    override final def comonad: Comonad[F] = this
  }

  /** Provides syntax for [[Comonad]]. */
  trait Syntax extends Serializable {
    implicit final def comonadOps[F[_] : Comonad, A](fa: F[A]): Ops[F, A] =
      new Ops(fa)
  }

  /** Extension methods for [[Comonad]]. */
  final class Ops[F[_], A](self: F[A])(implicit F: Comonad[F])
    extends Serializable {

    /** Extension method for [[Comonad.extract]]. */
    def extract: A = F.extract(self)
  }
}