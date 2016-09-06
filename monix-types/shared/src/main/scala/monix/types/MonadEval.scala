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

import monix.types.utils._

/** Type-class describing a [[Monad]] which also supports
  * lifting a by-name value into the monadic context.
  *
  * To implement `MonadEval`:
  *
  *  - inherit from [[MonadEval.Type]] in derived type-classes
  *  - inherit from [[MonadEval.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scado
  * project and [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been inspired by [[http://typelevel.org/cats/ Cats]] and
  * [[https://github.com/functional-streams-for-scala/fs2 FS2]].
  */
trait MonadEval[F[_]] extends Serializable with Monad.Type[F] {
  self: MonadEval.Instance[F] =>

  def eval[A](a: => A): F[A]
}

object MonadEval {
  @inline def apply[F[_]](implicit F: MonadEval[F]): MonadEval[F] = F

  /** The `MonadEval.Type` should be inherited in type-classes that
    * are derived from [[MonadEval]].
    */
  trait Type[F[_]] extends Monad.Type[F] {
    implicit def monadEval: MonadEval[F]
  }

  /** The `MonadEval.Instance` provides the means to combine
    * [[MonadEval]] instances with other type-classes.
    *
    * To be inherited by `MonadEval` instances.
    */
  trait Instance[F[_]] extends MonadEval[F] with Type[F]
    with Monad.Instance[F] {

    override final def monadEval: MonadEval[F] = this
  }

  /** Laws for [[MonadEval]]. */
  trait Laws[F[_]] extends Monad.Laws[F] with Type[F] {
    private def A = applicative
    private def E = monadEval

    def evalEquivalenceWithPure[A](a: A): IsEquiv[F[A]] =
      E.eval(a) <-> A.pure(a)

    def evalEquivalenceWithRaiseError[A](ex: Throwable)
      (implicit M: MonadError[F, Throwable]): IsEquiv[F[A]] =
      E.eval[A](throw ex) <-> M.raiseError[A](ex)
  }
}
