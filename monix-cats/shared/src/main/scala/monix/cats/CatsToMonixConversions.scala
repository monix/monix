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

import _root_.cats.{Functor => CatsFunctor}
import _root_.cats.{Applicative => CatsApplicative}
import _root_.cats.{Monad => CatsMonad}
import _root_.cats.{ApplicativeError => CatsApplicativeError}
import _root_.cats.{CoflatMap => CatsCoflatMap}
import _root_.cats.{Comonad => CatsComonad}
import _root_.cats.{MonadFilter => CatsMonadFilter}
import _root_.cats.{SemigroupK => CatsSemigroupK}
import _root_.cats.{MonoidK => CatsMonoidK}
import _root_.cats.{RecursiveTailRecM => CatsRecursiveTailRecM}
import monix.types._

/** Defines conversions from [[http://typelevel.org/cats/ Cats]]
  * type-class instances to the Monix type-classes defined in
  * [[monix.types]].
  */
trait CatsToMonixConversions extends CatsCoreToMonix9

private[cats] trait CatsCoreToMonix0 {
  /** Converts Cats type instances into Monix's
    * [[monix.types.Functor Functor]]
    */
  implicit def catsToMonixFunctor[F[_]](implicit ev: CatsFunctor[F]): Functor[F] =
    new CatsToMonixFunctor[F] { override val F = ev }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.Functor Functor]]
    */
  trait CatsToMonixFunctor[F[_]] extends FunctorClass[F] {
    val F: CatsFunctor[F]
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.map(fa)(f)
  }
}

private[cats] trait CatsCoreToMonix1 extends CatsCoreToMonix0 {
  /** Converts Cats type instances into Monix's
    * [[monix.types.Applicative Applicative]]
    */
  implicit def catsToMonixApplicative[F[_]](implicit ev: CatsApplicative[F]): Applicative[F] =
    new CatsToMonixApplicative[F] { override val F = ev }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.Applicative Applicative]]
    */
  trait CatsToMonixApplicative[F[_]]
    extends ApplicativeClass[F] with CatsToMonixFunctor[F] {

    override val F: CatsApplicative[F]

    override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
      F.map2(fa,fb)(f)
    override def pure[A](a: A): F[A] =
      F.pure(a)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] =
      F.ap(ff)(fa)
  }
}

private[cats] trait CatsCoreToMonix2 extends CatsCoreToMonix1 {
  /** Converts Cats type instances into Monix's
    * [[monix.types.Recoverable Recoverable]]
    */
  implicit def catsToMonixRecoverable[F[_],E](implicit ev: CatsApplicativeError[F,E]): Recoverable[F,E] =
    new CatsToMonixRecoverable[F,E] { override val F = ev }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.Recoverable Recoverable]]
    */
  trait CatsToMonixRecoverable[F[_],E]
    extends RecoverableClass[F,E] with CatsToMonixApplicative[F] {

    override val F: CatsApplicativeError[F,E]

    def raiseError[A](e: E): F[A] =
      F.raiseError(e)
    def onErrorRecover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A] =
      F.recover(fa)(pf)
    def onErrorRecoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
      F.recoverWith(fa)(pf)
    def onErrorHandleWith[A](fa: F[A])(f: (E) => F[A]): F[A] =
      F.handleErrorWith(fa)(f)
    def onErrorHandle[A](fa: F[A])(f: (E) => A): F[A] =
      F.handleError(fa)(f)
  }
}

private[cats] trait CatsCoreToMonix3 extends CatsCoreToMonix2 {
  /** Converts Cats type instances into Monix's
    * [[monix.types.Monad Monad]]
    */
  implicit def convertCatsToMonixMonad[F[_]](implicit ev: CatsMonad[F]): Monad[F] =
    new CatsToMonixMonad[F] { override val F = ev }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.Monad Monad]]
    */
  trait CatsToMonixMonad[F[_]]
    extends MonadClass[F] with CatsToMonixApplicative[F] {

    override val F: CatsMonad[F]

    override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      F.flatMap(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] =
      F.flatten(ffa)
  }
}

private[cats] trait CatsCoreToMonix4 extends CatsCoreToMonix3 {
  /** Converts Cats type instances into Monix's
    * [[monix.types.CoflatMap CoflatMap]]
    */
  implicit def catsToMonixCoflatMap[F[_]](implicit ev: CatsCoflatMap[F]): CoflatMap[F] =
    new CatsToMonixCoflatMap[F] { override val F = ev }

  /** Converts Cats type instances into Monix's
    * [[monix.types.CoflatMap CoflatMap]]
    */
  trait CatsToMonixCoflatMap[F[_]]
    extends CoflatMapClass[F] with CatsToMonixFunctor[F] {

    override val F: CatsCoflatMap[F]

    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      F.coflatMap(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] =
      F.coflatten(fa)
  }
}

private[cats] trait CatsCoreToMonix5 extends CatsCoreToMonix4 {
  /** Converts Cats type instances into the Monix's
    * [[monix.types.Comonad Comonad]]
    */
  implicit def catsToMonixComonad[F[_]](implicit ev: CatsComonad[F]): Comonad[F] =
    new CatsToMonixComonad[F] { override val F = ev }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.MonadFilter MonadFilter]]
    */
  trait CatsToMonixComonad[F[_]]
    extends ComonadClass[F] with CatsToMonixCoflatMap[F] {

    override val F: CatsComonad[F]
    override def extract[A](x: F[A]): A = F.extract(x)
  }
}

private[cats] trait CatsCoreToMonix6 extends CatsCoreToMonix5 {
  /** Converts Cats type instances into the Monix's
    * [[monix.types.MonadFilter MonadFilter]]
    */
  implicit def catsToMonixMonadFilter[F[_]](implicit ev: CatsMonadFilter[F]): MonadFilter[F] =
    new CatsToMonixMonadFilter[F] { override val F = ev }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.MonadFilter MonadFilter]]
    */
  trait CatsToMonixMonadFilter[F[_]]
    extends MonadFilterClass[F] with CatsToMonixMonad[F] {

    override val F: CatsMonadFilter[F]

    override def empty[A]: F[A] =
      F.empty[A]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      F.filter(fa)(f)
  }
}

private[cats] trait CatsCoreToMonix7 extends CatsCoreToMonix6 {
  /** Converts Cats type instances into Monix's
    * [[monix.types.SemigroupK SemigroupK]]
    */
  implicit def catsToMonixSemigroupK[F[_]](implicit ev: CatsSemigroupK[F]): SemigroupK[F] =
    new CatsToMonixSemigroupK[F] { override val F = ev }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.SemigroupK SemigroupK]]
    */
  trait CatsToMonixSemigroupK[F[_]]
    extends SemigroupKClass[F] {

    val F: CatsSemigroupK[F]
    override def combineK[A](x: F[A], y: F[A]): F[A] =
      F.combineK(x,y)
  }
}

private[cats] trait CatsCoreToMonix8 extends CatsCoreToMonix7 {
  /** Converts Cats type instances into Monix's
    * [[monix.types.MonoidK MonoidK]]
    */
  implicit def catsToMonixMonoidK[F[_]](implicit ev: CatsMonoidK[F]): MonoidK[F] =
    new CatsToMonixMonoidK[F] { override val F = ev }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.MonoidK MonoidK]]
    */
  trait CatsToMonixMonoidK[F[_]]
    extends MonoidKClass[F] with CatsToMonixSemigroupK[F] {

    override val F: CatsMonoidK[F]
    override def empty[A]: F[A] = F.empty[A]
  }
}

private[cats] trait CatsCoreToMonix9 extends CatsCoreToMonix8 {
  /** Converts Cats type instances into Monix's
    * [[monix.types.MonadRec MonadRec]]
    */
  implicit def catsToMonixMonadRec[F[_]]
    (implicit ev1: CatsMonad[F], ev2: CatsRecursiveTailRecM[F]): MonadRec[F] =
    new CatsToMonixMonadRec[F] { override val F = ev2.sameType(ev1) }

  /** Adapter for converting Cats type instances into Monix's
    * [[monix.types.MonadRec MonadRec]].
    */
  trait CatsToMonixMonadRec[F[_]]
    extends MonadRecClass[F] with CatsToMonixMonad[F] {

    override val F: CatsMonad[F]
    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] =
      F.tailRecM(a)(f)
  }
}
