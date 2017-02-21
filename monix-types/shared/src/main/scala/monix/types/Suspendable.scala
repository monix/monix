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
  * To implement `Suspendable`:
  *
  *  - inherit from [[Suspendable.Type]] in derived type-classes
  *  - inherit from [[Suspendable.Instance]] when implementing instances
  *
  * The purpose of this type-class is to support the data-types in the
  * Monix library and it is considered a shim for a lawful type-class
  * to be supplied by libraries such as Cats or Scalaz or equivalent.
  *
  * CREDITS: The type-class encoding has been inspired by the Scato
  * project by Aloïs Cochard and [[https://github.com/scalaz/scalaz/ Scalaz 8]] and the type has
  * been inspired by [[http://typelevel.org/cats/ Cats]] and
  * [[https://github.com/functional-streams-for-scala/fs2 FS2]].
  */
trait Suspendable[F[_]] extends Serializable with MonadEval.Type[F] {
  self: Suspendable.Instance[F] =>

  def suspend[A](fa: => F[A]): F[A] =
    monad.flatten[A](eval(fa))
}

object Suspendable {
  @inline def apply[F[_]](implicit F: Suspendable[F]): Suspendable[F] = F

  /** The `Suspendable.Type` should be inherited in type-classes that
    * are derived from [[Suspendable]].
    */
  trait Type[F[_]] extends MonadEval.Type[F] {
    implicit def suspendable: Suspendable[F]
  }

  /** The `Suspendable.Instance` provides the means to combine
    * [[Suspendable]] instances with other type-classes.
    *
    * To be inherited by `Suspendable` instances.
    */
  trait Instance[F[_]] extends Suspendable[F] with Type[F]
    with MonadEval.Instance[F] {

    override final def suspendable: Suspendable[F] = this
  }

  /** Laws for [[Suspendable]]. */
  trait Laws[F[_]] extends MonadEval.Laws[F] with Type[F] {
    private def A = applicative
    private def E = monadEval
    private def M = monad
    private def S = suspendable

    def suspendEquivalenceWithEval[A](a: A): IsEquiv[F[A]] =
      S.suspend(A.pure(a)) <-> E.eval(a)

    def evalEquivalenceWithSuspend[A](fa: F[A]): IsEquiv[F[A]] =
      M.flatten[A](E.eval(fa)) <-> S.suspend(fa)

    def suspendDelaysEffects[A](seed: A, effect: A => A): IsEquiv[F[A]] = {
      var atom = seed
      val fa = S.suspend { atom = effect(atom); A.pure(seed) }
      fa <-> A.pure(atom)
    }

    def evalDelaysEffects[A](seed: A, effect: A => A): IsEquiv[F[A]] = {
      var atom = seed
      val fa = S.monadEval.eval { atom = effect(atom); seed }
      fa <-> A.pure(atom)
    }
  }
}
