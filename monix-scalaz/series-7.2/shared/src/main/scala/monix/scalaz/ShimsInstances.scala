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

package monix.scalaz

import monix.types.shims._

/** Groups all shim type-class conversions together. */
trait ShimsInstances extends ShimsLevel8

private[scalaz] trait ShimsLevel8 extends ShimsLevel7 {
  /** Converts Monix's [[monix.types.shims.MonadPlus MonadPlus]]
    * instances into the Scalaz `MonadPlus`.
    */
  implicit def monixMonadPlusInstancesToScalaz[F[_]]
    (implicit ev: MonadPlus[F]): _root_.scalaz.MonadPlus[F] =
    new ConvertMonixMonadPlusToScalaz[F] { override val F = ev }

  private[scalaz] trait ConvertMonixMonadPlusToScalaz[F[_]]
    extends ConvertMonixMonadToScalaz[F]
      with ConvertMonixMonoidKToScalaz[F]
      with _root_.scalaz.MonadPlus[F] {

    override val F: MonadPlus[F]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] = F.filter(fa)(f)
  }
}

private[scalaz] trait ShimsLevel7 extends ShimsLevel6 {
  /** Converts Monix's [[monix.types.shims.MonoidK MonoidK]]
    * instances into the Scalaz `PlusEmpty`.
    */
  implicit def monixMonoidKInstancesToScalaz[F[_]]
    (implicit ev: MonoidK[F]): _root_.scalaz.PlusEmpty[F] =
    new ConvertMonixMonoidKToScalaz[F] { override val F = ev }

  private[scalaz] trait ConvertMonixMonoidKToScalaz[F[_]]
    extends ConvertMonixSemigroupKToScalaz[F] with _root_.scalaz.PlusEmpty[F] {

    override val F: MonoidK[F]
    override def empty[A]: F[A] = F.empty[A]
  }
}

private[scalaz] trait ShimsLevel6 extends ShimsLevel5 {
  /** Converts Monix's [[monix.types.shims.SemigroupK SemigroupK]]
    * instances into the Scalaz `Plus`.
    */
  implicit def monixSemigroupKInstancesToScalaz[F[_]]
    (implicit ev: SemigroupK[F]): _root_.scalaz.Plus[F] =
    new ConvertMonixSemigroupKToScalaz[F] {
      override val F = ev
    }

  private[scalaz] trait ConvertMonixSemigroupKToScalaz[F[_]]
    extends _root_.scalaz.Plus[F] {

    val F: SemigroupK[F]
    override def plus[A](a: F[A], b: => F[A]): F[A] = F.combineK(a, b)
  }
}

private[scalaz] trait ShimsLevel5 extends ShimsLevel4 {
  /** Converts Monix's [[monix.types.shims.Comonad Comonad]]
    * instances into the Scalaz `Comonad`.
    */
  implicit def monixComonadInstancesToScalaz[F[_]]
    (implicit ev: Comonad[F]): _root_.scalaz.Comonad[F] =
    new ConvertMonixComonadToScalaz[F] { override val F = ev }

  private[scalaz] trait ConvertMonixComonadToScalaz[F[_]]
    extends ConvertMonixCoflatMapToScalaz[F] with _root_.scalaz.Comonad[F] {

    override val F: Comonad[F]
    override def copoint[A](p: F[A]): A = F.extract(p)
  }
}

private[scalaz] trait ShimsLevel4 extends ShimsLevel3 {
  /** Converts Monix's [[monix.types.shims.CoflatMap CoflatMap]]
    * instances into the Scalaz `Cobind`.
    */
  implicit def monixCoflatMapInstancesToScalaz[F[_]]
    (implicit ev: CoflatMap[F]): _root_.scalaz.Cobind[F] =
    new ConvertMonixCoflatMapToScalaz[F] { override val F = ev }

  private[scalaz] trait ConvertMonixCoflatMapToScalaz[F[_]]
    extends ConvertMonixFunctorToScalaz[F] with _root_.scalaz.Cobind[F] {

    override val F: CoflatMap[F]
    override def cobind[A, B](fa: F[A])(f: (F[A]) => B): F[B] = F.coflatMap(fa)(f)
  }
}

private[scalaz] trait ShimsLevel3 extends ShimsLevel2  {
  /** Converts Monix's [[monix.types.shims.MonadError MonadError]]
    * instances into the Scalaz `MonadError`.
    */
  implicit def monixMonadErrorInstancesToScalaz[F[_],E]
    (implicit ev: MonadError[F,E]): _root_.scalaz.MonadError[F,E] =
    new ConvertMonixMonadErrorToScalaz[F,E] { override val F: MonadError[F,E] = ev }

  private[scalaz] trait ConvertMonixMonadErrorToScalaz[F[_],E]
    extends ConvertMonixMonadToScalaz[F] with _root_.scalaz.MonadError[F,E] {

    override val F: MonadError[F,E]

    override def raiseError[A](e: E): F[A] = F.raiseError(e)
    override def handleError[A](fa: F[A])(f: (E) => F[A]): F[A] =
      F.handleErrorWith(fa)(f)
  }
}

private[scalaz] trait ShimsLevel2 extends ShimsLevel1 {
  /** Converts Monix's [[monix.types.shims.Monad Monad]]
    * instances into the Scalaz `Monad`.
    */
  implicit def monixMonadInstancesToScalaz[F[_]](implicit ev: Monad[F]): _root_.scalaz.Monad[F] =
    new ConvertMonixMonadToScalaz[F] { override val F = ev }

  private[scalaz] trait ConvertMonixMonadToScalaz[F[_]]
    extends ConvertMonixApplicativeToScalaz[F] with _root_.scalaz.Monad[F] {

    override val F: Monad[F]
    override def bind[A, B](fa: F[A])(f: (A) => F[B]): F[B] = F.flatMap(fa)(f)
  }
}

private[scalaz] trait ShimsLevel1 extends ShimsLevel0 {
  /** Converts Monix's [[monix.types.shims.Applicative Applicative]]
    * instances into the Scalaz `Applicative`.
    */
  implicit def monixApplicativeInstancesToScalaz[F[_]]
    (implicit ev: Applicative[F]): _root_.scalaz.Applicative[F] =
    new ConvertMonixApplicativeToScalaz[F] { override val F = ev }

  private[scalaz] trait ConvertMonixApplicativeToScalaz[F[_]]
    extends ConvertMonixFunctorToScalaz[F] with _root_.scalaz.Applicative[F] {

    override val F: Applicative[F]
    override def point[A](a: => A): F[A] = F.pureEval(a)
    override def ap[A, B](fa: => F[A])(f: => F[(A) => B]): F[B] = F.ap(fa)(f)
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] = F.map(fa)(f)
    override def apply2[A, B, C](fa: => F[A], fb: => F[B])(f: (A, B) => C): F[C] = F.map2(fa, fb)(f)
  }
}

private[scalaz] trait ShimsLevel0 {
  /** Converts Monix's [[monix.types.shims.Functor Functor]]
    * instances into the Scalaz `Functor`.
    */
  implicit def monixFunctorInstancesToScalaz[F[_]]
    (implicit ev: Functor[F]): _root_.scalaz.Functor[F] =
    new ConvertMonixFunctorToScalaz[F] { override val F = ev }

  private[scalaz] trait ConvertMonixFunctorToScalaz[F[_]]
    extends _root_.scalaz.Functor[F] {

    val F: Functor[F]
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] = F.map(fa)(f)
  }
}