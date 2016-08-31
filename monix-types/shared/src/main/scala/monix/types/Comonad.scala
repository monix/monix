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
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * To implement it in instances, inherit from [[ComonadClass]].
  *
  * Credit should be given where it is due.The type-class encoding has
  * been copied from the Scado project and
  * [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Comonad[F[_]] extends Serializable {
  def coflatMap: CoflatMap[F]
  def extract[A](x: F[A]): A
}

object Comonad extends ComonadSyntax {
  @inline def apply[F[_]](implicit F: Comonad[F]): Comonad[F] = F
}

/** The `ComonadClass` provides the means to combine
  * [[Comonad]] instances with other type-classes.
  *
  * To be inherited by `Comonad` instances.
  */
trait ComonadClass[F[_]] extends Comonad[F] with CoflatMapClass[F] {
  final def comonad: Comonad[F] = this
}

/** Provides syntax for [[Comonad]]. */
trait ComonadSyntax extends Serializable {
  implicit final def comonadOps[F[_], A](fa: F[A])
    (implicit F: Comonad[F]): ComonadSyntax.Ops[F, A] =
    new ComonadSyntax.Ops(fa)
}

object ComonadSyntax {
  final class Ops[F[_], A](self: F[A])(implicit F: Comonad[F])
    extends Serializable {

    /** Extension method for [[Comonad.extract]]. */
    def extract: A = F.extract(self)
  }
}
