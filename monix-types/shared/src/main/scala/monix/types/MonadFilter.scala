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

/** The `MonadFilter` type-class is equipped with an additional
  * operation which allows us to create an "Empty" value for the Monad
  * (for whatever "empty" makes sense for that particular monad). This
  * is of particular interest to us since it allows us to add a
  * `filter` method to a Monad, which is used when pattern matching or
  * using guards in for comprehensions.
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * To implement it in instances, inherit from [[MonadFilterClass]].
  *
  * Credit should be given where it is due.The type-class encoding has
  * been copied from the Scado project and
  * [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait MonadFilter[F[_]] extends Serializable {
  def monad: Monad[F]

  def empty[A]: F[A]
  def filter[A](fa: F[A])(f: A => Boolean): F[A]
}

object MonadFilter {
  @inline def apply[F[_]](implicit F: MonadFilter[F]): MonadFilter[F] = F
}

/** The `MonadFilterClass` provides the means to combine
  * [[MonadFilter]] instances with other type-classes.
  *
  * To be inherited by `MonadFilter` instances.
  */
trait MonadFilterClass[F[_]] extends MonadFilter[F] with MonadClass[F] {
  final def monadFilter: MonadFilter[F] = this
}
