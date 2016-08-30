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

/** A marker type that describes applicatives that eventually evaluate
  * to a single result (e.g. `Task`, `Coeval`).
  *
  * In particular this means:
  *
  *  - the `flatMap` operation is tail-recursive
  *  - the evaluation can be suspended and memoized
  *  - if `A` has a `Semigroup[A]` defined, then `F[A]` also gets a
  *    `Semigroup[F[A]]` automatically
  *  - if `A` has a `Monoid[A]` defined, then `F[A]` also gets a 
  *    `Monoid[F[A]]` automatically
  *  - if `A` has a `Group[A]` defined, then `F[A]` also gets a 
  *    `Group[F[A]]` automatically
  * 
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  * 
  * To implement it in instances, inherit from [[EvaluableClass]].
  * 
  * Credit should be given where it is due. The type-class encoding has
  * been copied from the Scado project and
  * [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been extracted from [[http://typelevel.org/cats/ Cats]].
  */
trait Evaluable[F[_]] extends Any with Serializable {
  def monadRec: MonadRec[F]
  def memoizable: Memoizable[F]
  def coflatMap: CoflatMap[F]
}

object Evaluable {
  @inline def apply[F[_]](implicit F: Evaluable[F]): Evaluable[F] = F
}

/** The `EvaluableClass` provides the means to combine
  * [[Evaluable]] instances with other type-classes.
  * 
  * To be inherited by `Evaluable` instances.
  */
trait EvaluableClass[F[_]] extends Evaluable[F] with MemoizableClass[F] {
  final def evaluable: Evaluable[F] = this
}

