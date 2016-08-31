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

/** Type-class describing an [[Applicative]] which supports
  * capturing a deferred evaluation of a by-name `F[A]`.
  *
  * Evaluation can be suspended until a value is extracted.
  * The `suspend` operation can be thought of as a factory
  * of `F[A]` instances, that will produce fresh instances,
  * along with possible side-effects, on each evaluation.
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * To implement it in instances, inherit from [[DeferrableClass]].
  *
  * Credit should be given where it is due. The type-class encoding has
  * been copied from the Scado project and
  * [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been inspired by [[http://typelevel.org/cats/ Cats]] and
  * [[https://github.com/functional-streams-for-scala/fs2 FS2]].
  */
trait Deferrable[F[_]] extends Serializable {
  def applicative: Applicative[F]

  def defer[A](fa: => F[A]): F[A]

  def eval[A](a: => A): F[A] =
    defer(applicative.pure(a))
}

object Deferrable {
  @inline def apply[F[_]](implicit F: Deferrable[F]): Deferrable[F] = F
}

/** The `DeferrableClass` provides the means to combine
  * [[Deferrable]] instances with other type-classes.
  *
  * To be inherited by `Deferrable` instances.
  */
trait DeferrableClass[F[_]] extends Deferrable[F]
  with ApplicativeClass[F] {

  final def suspendable: Deferrable[F] = this
}
