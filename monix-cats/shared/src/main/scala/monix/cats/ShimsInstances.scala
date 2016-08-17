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

package monix.cats

import cats.Eval
import monix.types.shims._

/** Groups all shim type-class conversions together. */
trait ShimsInstances extends ShimsLevel11

private[cats] trait ShimsLevel11 extends  ShimsLevel10 {
  /** Converts Monix's [[monix.types.shims.MonadPlus MonadPlus]]
    * instances into the Cats `MonadCombine`.
    */
  implicit def monixMonadPlusInstancesToCats[F[_]]
    (implicit ev: MonadPlus[F]): _root_.cats.MonadCombine[F] =
    new ConvertMonixMonadPlusToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixMonadPlusToCats[F[_]]
    extends _root_.cats.MonadCombine[F]
      with ConvertMonixMonoidKToCats[F]
      with ConvertMonixMonadFilterToCats[F] {

    override val F: MonadPlus[F]
    override def empty[A]: F[A] = F.empty[A]
  }
}

private[cats] trait ShimsLevel10 extends ShimsLevel9 {
  /** Converts Monix's [[monix.types.shims.MonoidK MonoidK]]
    * instances into the Cats `MonoidK`.
    */
  implicit def monixMonoidKInstancesToCats[F[_]]
    (implicit ev: MonoidK[F]): _root_.cats.MonoidK[F] =
    new ConvertMonixMonoidKToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixMonoidKToCats[F[_]]
    extends _root_.cats.MonoidK[F] with ConvertMonixSemigroupKToCats[F] {

    override val F: MonoidK[F]
    override def empty[A]: F[A] = F.empty[A]
    override def combineK[A](x: F[A], y: F[A]): F[A] = F.combineK(x,y)
  }
}

private[cats] trait ShimsLevel9 extends ShimsLevel8 {
  /** Converts Monix's [[monix.types.shims.SemigroupK SemigroupK]]
    * instances into the Cats `SemigroupK`.
    */
  implicit def monixSemigroupKInstancesToCats[F[_]]
    (implicit ev: SemigroupK[F]): _root_.cats.SemigroupK[F] =
    new ConvertMonixSemigroupKToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixSemigroupKToCats[F[_]]
    extends _root_.cats.SemigroupK[F] {

    val F: SemigroupK[F]
    override def combineK[A](x: F[A], y: F[A]): F[A] = F.combineK(x,y)
  }
}

private[cats] trait ShimsLevel8 extends ShimsLevel7 {
  /** Converts Monix's [[monix.types.shims.MonadFilter MonadFilter]]
    * instances into the Cats `MonadFilter`.
    */
  implicit def monixMonadFilterInstancesToCats[F[_]]
    (implicit ev: MonadFilter[F]): _root_.cats.MonadFilter[F] =
    new ConvertMonixMonadFilterToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixMonadFilterToCats[F[_]]
    extends _root_.cats.MonadFilter[F] with ConvertMonixMonadToCats[F] {

    override val F: MonadFilter[F]
    override def empty[A]: F[A] = F.empty[A]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] = F.filter(fa)(f)
    override def filterM[A](fa: F[A])(f: (A) => F[Boolean]): F[A] = F.filterM(fa)(f)
  }
}

private[cats] trait ShimsLevel7 extends ShimsLevel6 {
  /** Converts Monix's [[monix.types.shims.Bimonad Bimonad]]
    * instances into the Cats `Bimonad`.
    */
  implicit def monixBimonadInstancesToCats[F[_]]
    (implicit ev: Bimonad[F]): _root_.cats.Bimonad[F] =
    new ConvertMonixBimonadToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixBimonadToCats[F[_]]
    extends _root_.cats.Bimonad[F] with ConvertMonixMonadToCats[F] {

    override val F: Bimonad[F]
    override def extract[A](x: F[A]): A = F.extract(x)
    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] = F.coflatMap(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] = F.coflatten(fa)
  }
}

private[cats] trait ShimsLevel6 extends ShimsLevel5 {
  /** Converts Monix's [[monix.types.shims.Comonad Comonad]]
    * instances into the Cats `Comonad`.
    */
  implicit def monixComonadInstancesToCats[F[_]]
    (implicit ev: Comonad[F]): _root_.cats.Comonad[F] =
    new ConvertMonixComonadToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixComonadToCats[F[_]]
    extends _root_.cats.Comonad[F] with ConvertMonixCoflatMapToCats[F] {

    override val F: Comonad[F]
    override def extract[A](x: F[A]): A = F.extract(x)
  }
}

private[cats] trait ShimsLevel5 extends ShimsLevel4 {
  /** Converts Monix's [[monix.types.shims.CoflatMap CoflatMap]]
    * instances into the Cats `CoflatMap`.
    */
  implicit def monixCoflatMapInstancesToCats[F[_]]
    (implicit ev: CoflatMap[F]): _root_.cats.CoflatMap[F] =
    new ConvertMonixCoflatMapToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixCoflatMapToCats[F[_]]
    extends _root_.cats.CoflatMap[F] with ConvertMonixFunctorToCats[F] {

    override val F: CoflatMap[F]
    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] = F.coflatMap(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] = F.coflatten(fa)
  }
}

private[cats] trait ShimsLevel4 extends ShimsLevel3  {
  /** Converts Monix's [[monix.types.shims.MonadError MonadError]]
    * instances into the Cats `MonadError`.
    */
  implicit def monixMonadErrorInstancesToCats[F[_],E]
    (implicit ev: MonadError[F,E]): _root_.cats.MonadError[F,E] =
    new ConvertMonixMonadErrorToCats[F,E] { override val F: MonadError[F,E] = ev }

  private[cats] trait ConvertMonixMonadErrorToCats[F[_],E]
    extends _root_.cats.MonadError[F,E]
      with ConvertMonixMonadToCats[F]
      with ConvertMonixApplicativeErrorToCats[F,E] {

    override val F: MonadError[F,E]
  }
}

private[cats] trait ShimsLevel3 extends ShimsLevel2 {
  /** Converts Monix's [[monix.types.shims.Monad Monad]]
    * instances into the Cats `Monad`.
    */
  implicit def monixMonadInstancesToCats[F[_]](implicit ev: Monad[F]): _root_.cats.Monad[F] =
    new ConvertMonixMonadToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixMonadToCats[F[_]]
    extends _root_.cats.Monad[F] with ConvertMonixApplicativeToCats[F] {

    override val F: Monad[F]
    override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] = F.flatMap(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] = F.flatten(ffa)
  }
}

private[cats] trait ShimsLevel2 extends ShimsLevel1 {
  /** Converts Monix's [[monix.types.shims.ApplicativeError ApplicativeError]]
    * instances into the Cats `ApplicativeError`.
    */
  implicit def monixApplicativeErrorInstancesToCats[F[_],E]
    (implicit ev: ApplicativeError[F,E]): _root_.cats.ApplicativeError[F,E] =
    new ConvertMonixApplicativeErrorToCats[F,E] { override val F = ev }

  private[cats] trait ConvertMonixApplicativeErrorToCats[F[_],E]
    extends _root_.cats.ApplicativeError[F,E]
      with ConvertMonixApplicativeToCats[F] {

    override val F: ApplicativeError[F,E]
    override def raiseError[A](e: E): F[A] = F.raiseError(e)
    override def handleErrorWith[A](fa: F[A])(f: (E) => F[A]): F[A] = F.handleErrorWith(fa)(f)
    override def handleError[A](fa: F[A])(f: (E) => A): F[A] = F.handleError(fa)(f)
    override def recover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A] = F.recover(fa)(pf)
    override def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] = F.recoverWith(fa)(pf)
  }
}

private[cats] trait ShimsLevel1 extends ShimsLevel0 {
  /** Converts Monix's [[monix.types.shims.Applicative Applicative]]
    * instances into the Cats `Applicative`.
    */
  implicit def monixApplicativeInstancesToCats[F[_]]
    (implicit ev: Applicative[F]): _root_.cats.Applicative[F] =
    new ConvertMonixApplicativeToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixApplicativeToCats[F[_]]
    extends _root_.cats.Applicative[F] with ConvertMonixFunctorToCats[F] {

    override val F: Applicative[F]
    override def pure[A](x: A): F[A] = F.pure(x)
    override def pureEval[A](x: Eval[A]): F[A] = F.pureEval(x.value)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] = F.ap(fa)(ff)
    override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] = F.map2(fa,fb)(f)
    override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = F.map2(fa,fb)((a,b) => (a,b))
  }
}

private[cats] trait ShimsLevel0 {
  /** Converts Monix's [[monix.types.shims.Functor Functor]]
    * instances into the Cats `Functor`.
    */
  implicit def monixFunctorInstancesToCats[F[_]]
    (implicit ev: Functor[F]): _root_.cats.Functor[F] =
    new ConvertMonixFunctorToCats[F] { override val F = ev }

  private[cats] trait ConvertMonixFunctorToCats[F[_]]
    extends _root_.cats.Functor[F] {

    val F: Functor[F]
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] = F.map(fa)(f)
  }
}