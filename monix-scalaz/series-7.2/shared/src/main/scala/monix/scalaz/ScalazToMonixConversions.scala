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

import monix.types._
import _root_.scalaz.\/
import _root_.scalaz.{Applicative => ScalazApplicative}
import _root_.scalaz.{BindRec => ScalazBindRec}
import _root_.scalaz.{Cobind => ScalazCobind}
import _root_.scalaz.{Comonad => ScalazComonad}
import _root_.scalaz.{Functor => ScalazFunctor}
import _root_.scalaz.{Monad => ScalazMonad}
import _root_.scalaz.{MonadError => ScalazMonadError}
import _root_.scalaz.{MonadPlus => ScalazMonadFilter}
import _root_.scalaz.{Plus => ScalazPlus}
import _root_.scalaz.{PlusEmpty => ScalazPlusEmpty}

/** Defines conversions from [[http://typelevel.org/cats/ Cats]]
  * type-class instances to the Monix type-classes defined in
  * [[monix.types]].
  */
trait ScalazToMonixConversions extends ScalazToMonix9

private[scalaz] trait ScalazToMonix0 {
  /** Converts Scalaz type instances into Monix's
    * [[monix.types.Functor Functor]]
    */
  implicit def ScalazToMonixFunctor[F[_]](implicit ev: ScalazFunctor[F]): Functor[F] =
    new ScalazToMonixFunctor[F] { override def szF = ev }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.Functor Functor]]
    */
  trait ScalazToMonixFunctor[F[_]] extends Functor.Instance[F] {
    def szF: ScalazFunctor[F]

    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      szF.map(fa)(f)
  }
}

private[scalaz] trait ScalazToMonix1 extends ScalazToMonix0 {
  /** Converts Scalaz type instances into Monix's
    * [[monix.types.Applicative Applicative]]
    */
  implicit def ScalazToMonixApplicative[F[_]](implicit ev: ScalazApplicative[F]): Applicative[F] =
    new ScalazToMonixApplicative[F] { override def szF = ev }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.Applicative Applicative]]
    */
  trait ScalazToMonixApplicative[F[_]]
    extends Applicative.Instance[F] with ScalazToMonixFunctor[F] {

    override def szF: ScalazApplicative[F]

    override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
      szF.apply2(fa,fb)(f)
    override def pure[A](a: A): F[A] =
      szF.pure(a)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] =
      szF.ap(fa)(ff)
  }
}

private[scalaz] trait ScalazToMonix2 extends ScalazToMonix1 {
  /** Converts Scalaz type instances into Monix's
    * [[monix.types.Monad Monad]]
    */
  implicit def convertScalazToMonixMonad[F[_]](implicit ev: ScalazMonad[F]): Monad[F] =
    new ScalazToMonixMonad[F] { override def szF = ev }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.Monad Monad]]
    */
  trait ScalazToMonixMonad[F[_]]
    extends Monad.Instance[F] with ScalazToMonixApplicative[F] {

    override def szF: ScalazMonad[F]

    override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      szF.bind(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] =
      szF.join(ffa)
  }
}

private[scalaz] trait ScalazToMonix3 extends ScalazToMonix2 {
  /** Converts Scalaz type instances into Monix's
    * [[monix.types.MonadError MonadError]]
    */
  implicit def ScalazToMonixMonadError[F[_],E](implicit ev: ScalazMonadError[F,E]): MonadError[F,E] =
    new ScalazToMonixMonadError[F,E] { override def szF = ev }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.MonadError MonadError]]
    */
  trait ScalazToMonixMonadError[F[_],E]
    extends MonadError.Instance[F,E] with ScalazToMonixMonad[F] {

    override def szF: ScalazMonadError[F,E]

    def raiseError[A](e: E): F[A] =
      szF.raiseError(e)

    def onErrorRecover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A] =
      szF.handleError(fa) {
        case ex if pf.isDefinedAt(ex) => szF.pure(pf(ex))
        case other => szF.raiseError(other)
      }

    def onErrorRecoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
      szF.handleError(fa) {
        case ex if pf.isDefinedAt(ex) => pf(ex)
        case other => szF.raiseError(other)
      }

    def onErrorHandleWith[A](fa: F[A])(f: (E) => F[A]): F[A] =
      szF.handleError(fa)(f)
    def onErrorHandle[A](fa: F[A])(f: (E) => A): F[A] =
      szF.handleError(fa)(e => szF.pure(f(e)))
  }
}

private[scalaz] trait ScalazToMonix4 extends ScalazToMonix3 {
  /** Converts Scalaz type instances into Monix's
    * [[monix.types.Cobind CoflatMap]]
    */
  implicit def ScalazToMonixCoflatMap[F[_]](implicit ev: ScalazCobind[F]): Cobind[F] =
    new ScalazToMonixCobind[F] { override def szF = ev }

  /** Converts Scalaz type instances into Monix's
    * [[monix.types.Cobind CoflatMap]]
    */
  trait ScalazToMonixCobind[F[_]]
    extends Cobind.Instance[F] with ScalazToMonixFunctor[F] {

    override def szF: ScalazCobind[F]

    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      szF.cobind(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] =
      szF.cojoin(fa)
  }
}

private[scalaz] trait ScalazToMonix5 extends ScalazToMonix4 {
  /** Converts Scalaz type instances into the Monix's
    * [[monix.types.Comonad Comonad]]
    */
  implicit def ScalazToMonixComonad[F[_]](implicit ev: ScalazComonad[F]): Comonad[F] =
    new ScalazToMonixComonad[F] { override def szF = ev }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.MonadFilter MonadFilter]]
    */
  trait ScalazToMonixComonad[F[_]]
    extends Comonad.Instance[F] with ScalazToMonixCobind[F] {

    override def szF: ScalazComonad[F]
    override def extract[A](x: F[A]): A = szF.copoint(x)
  }
}

private[scalaz] trait ScalazToMonix6 extends ScalazToMonix5 {
  /** Converts Scalaz type instances into the Monix's
    * [[monix.types.MonadFilter MonadFilter]]
    */
  implicit def ScalazToMonixMonadFilter[F[_]](implicit ev: ScalazMonadFilter[F]): MonadFilter[F] =
    new ScalazToMonixMonadFilter[F] { override def szF = ev }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.MonadFilter MonadFilter]]
    */
  trait ScalazToMonixMonadFilter[F[_]]
    extends MonadFilter.Instance[F] with ScalazToMonixMonad[F] {

    override def szF: ScalazMonadFilter[F]

    override def empty[A]: F[A] =
      szF.empty[A]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      szF.filter(fa)(f)
  }
}

private[scalaz] trait ScalazToMonix7 extends ScalazToMonix6 {
  /** Converts Scalaz type instances into Monix's
    * [[monix.types.SemigroupK SemigroupK]]
    */
  implicit def ScalazToMonixSemigroupK[F[_]](implicit ev: ScalazPlus[F]): SemigroupK[F] =
    new ScalazToMonixSemigroupK[F] { override def szF = ev }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.SemigroupK SemigroupK]]
    */
  trait ScalazToMonixSemigroupK[F[_]]
    extends SemigroupK.Instance[F] {

    def szF: ScalazPlus[F]

    override def combineK[A](x: F[A], y: F[A]): F[A] =
      szF.plus(x,y)
  }
}

private[scalaz] trait ScalazToMonix8 extends ScalazToMonix7 {
  /** Converts Scalaz type instances into Monix's
    * [[monix.types.MonoidK MonoidK]]
    */
  implicit def ScalazToMonixMonoidK[F[_]](implicit ev: ScalazPlusEmpty[F]): MonoidK[F] =
    new ScalazToMonixMonoidK[F] { override def szF = ev }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.MonoidK MonoidK]]
    */
  trait ScalazToMonixMonoidK[F[_]]
    extends MonoidK.Instance[F] with ScalazToMonixSemigroupK[F] {

    override def szF: ScalazPlusEmpty[F]
    override def empty[A]: F[A] = szF.empty[A]
  }
}

private[scalaz] trait ScalazToMonix9 extends ScalazToMonix8 {
  /** Converts Scalaz type instances into Monix's
    * [[monix.types.MonadRec MonadRec]]
    */
  implicit def ScalazToMonixMonadRec[F[_]](implicit ev1: ScalazMonad[F], ev2: ScalazBindRec[F]): MonadRec[F] =
    new ScalazToMonixMonadRec[F] {
      override def szF = ev1
      override def szBindRec = ev2
    }

  /** Adapter for converting Scalaz type instances into Monix's
    * [[monix.types.MonadRec MonadRec]].
    */
  trait ScalazToMonixMonadRec[F[_]] extends MonadRec.Instance[F]
    with ScalazToMonixMonad[F] {

    def szBindRec: ScalazBindRec[F]

    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] = {
      val f2 = (a: A) => szF.map(f(a)) { case Right(r) => \/.right[A,B](r); case Left(l) => \/.left[A,B](l) }
      szBindRec.tailrecM(f2)(a)
    }
  }
}
