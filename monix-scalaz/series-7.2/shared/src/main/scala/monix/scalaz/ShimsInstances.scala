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
import scalaz.\/

/** Groups all shim type-class conversions together. */
trait ShimsInstances extends ShimsLevel9

private[scalaz] trait ShimsLevel9 extends ShimsLevel8 {
  /** Converts Monix's [[monix.types.MonadRec TailRecMonad]]
    * instances into the Scalaz `BindRec` + `Monad`.
    */
  implicit def monixTailRecMonadInstancesToScalaz[F[_]]
    (implicit ev: MonadRec[F]): _root_.scalaz.Monad[F] with _root_.scalaz.BindRec[F] =
    new ConvertMonixTailRecMonadToScalaz[F] {
      override val monadRec: MonadRec[F] = ev
      override val monad: Monad[F] = ev.monad
      override val applicative: Applicative[F] = ev.monad.applicative
      override val functor: Functor[F] = ev.monad.applicative.functor
    }

  private[scalaz] trait ConvertMonixTailRecMonadToScalaz[F[_]]
    extends ConvertMonixMonadToScalaz[F] with _root_.scalaz.BindRec[F] {

    val monadRec: MonadRec[F]
    override def tailrecM[A, B](f: (A) => F[\/[A, B]])(a: A): F[B] =
      monadRec.tailRecM(a)(a => functor.map(f(a))(_.toEither))
  }
}

private[scalaz] trait ShimsLevel8 extends ShimsLevel7 {
  /** Converts Monix's types instances into the Scalaz `MonadPlus`. */
  implicit def monixMonadPlusInstancesToScalaz[F[_]]
    (implicit MF: MonadFilter[F], MK: MonoidK[F]): _root_.scalaz.MonadPlus[F] =
    new ConvertMonixMonadPlusToScalaz[F] {
      override val monadFilter: MonadFilter[F] = MF
      override val semigroupK: SemigroupK[F] = MK.semigroupK
      override val monad: Monad[F] = MF.monad
      override val monoidK: MonoidK[F] = MK
      override val applicative: Applicative[F] = MF.monad.applicative
      override val functor: Functor[F] = MF.monad.applicative.functor
    }

  private[scalaz] trait ConvertMonixMonadPlusToScalaz[F[_]]
    extends ConvertMonixMonadToScalaz[F]
      with ConvertMonixMonoidKToScalaz[F]
      with _root_.scalaz.MonadPlus[F] {

    val monadFilter: MonadFilter[F]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      monadFilter.filter(fa)(f)
  }
}

private[scalaz] trait ShimsLevel7 extends ShimsLevel6 {
  /** Converts Monix's [[monix.types.MonoidK MonoidK]]
    * instances into the Scalaz `PlusEmpty`.
    */
  implicit def monixMonoidKInstancesToScalaz[F[_]]
    (implicit ev: MonoidK[F]): _root_.scalaz.PlusEmpty[F] =
    new ConvertMonixMonoidKToScalaz[F] {
      override val monoidK: MonoidK[F] = ev
      override val semigroupK: SemigroupK[F] = ev.semigroupK
    }

  private[scalaz] trait ConvertMonixMonoidKToScalaz[F[_]]
    extends ConvertMonixSemigroupKToScalaz[F] with _root_.scalaz.PlusEmpty[F] {

    val monoidK: MonoidK[F]
    override def empty[A]: F[A] = monoidK.empty[A]
  }
}

private[scalaz] trait ShimsLevel6 extends ShimsLevel5 {
  /** Converts Monix's [[monix.types.SemigroupK SemigroupK]]
    * instances into the Scalaz `Plus`.
    */
  implicit def monixSemigroupKInstancesToScalaz[F[_]]
    (implicit ev: SemigroupK[F]): _root_.scalaz.Plus[F] =
    new ConvertMonixSemigroupKToScalaz[F] {
      override val semigroupK = ev
    }

  private[scalaz] trait ConvertMonixSemigroupKToScalaz[F[_]]
    extends _root_.scalaz.Plus[F] {

    val semigroupK: SemigroupK[F]
    override def plus[A](a: F[A], b: => F[A]): F[A] =
      semigroupK.combineK(a, b)
  }
}

private[scalaz] trait ShimsLevel5 extends ShimsLevel4 {
  /** Converts Monix's [[monix.types.Comonad Comonad]]
    * instances into the Scalaz `Comonad`.
    */
  implicit def monixComonadInstancesToScalaz[F[_]]
    (implicit ev: Comonad[F]): _root_.scalaz.Comonad[F] =
    new ConvertMonixComonadToScalaz[F] {
      override val comonad: Comonad[F] = ev
      override val coflatMap: CoflatMap[F] = ev.coflatMap
      override val functor: Functor[F] = ev.coflatMap.functor
    }

  private[scalaz] trait ConvertMonixComonadToScalaz[F[_]]
    extends ConvertMonixCoflatMapToScalaz[F] with _root_.scalaz.Comonad[F] {

    val comonad: Comonad[F]
    override def copoint[A](p: F[A]): A =
      comonad.extract(p)
  }
}

private[scalaz] trait ShimsLevel4 extends ShimsLevel3 {
  /** Converts Monix's [[monix.types.CoflatMap CoflatMap]]
    * instances into the Scalaz `Cobind`.
    */
  implicit def monixCoflatMapInstancesToScalaz[F[_]]
    (implicit ev: CoflatMap[F]): _root_.scalaz.Cobind[F] =
    new ConvertMonixCoflatMapToScalaz[F] {
      override val coflatMap: CoflatMap[F] = ev
      override val functor: Functor[F] = ev.functor
    }

  private[scalaz] trait ConvertMonixCoflatMapToScalaz[F[_]]
    extends ConvertMonixFunctorToScalaz[F] with _root_.scalaz.Cobind[F] {

    val coflatMap: CoflatMap[F]
    override def cobind[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      coflatMap.coflatMap(fa)(f)
  }
}

private[scalaz] trait ShimsLevel3 extends ShimsLevel2  {
  /** Converts Monix's type instances into the Scalaz `MonadError`.
    */
  implicit def monixMonadErrorInstancesToScalaz[F[_],E]
    (implicit M: Monad[F], R: Recoverable[F,E]): _root_.scalaz.MonadError[F,E] =
    new ConvertMonixMonadErrorToScalaz[F,E] {
      override val recoverable: Recoverable[F, E] = R
      override val monad: Monad[F] = M
      override val applicative: Applicative[F] = M.applicative
      override val functor: Functor[F] = M.applicative.functor
    }

  private[scalaz] trait ConvertMonixMonadErrorToScalaz[F[_],E]
    extends ConvertMonixMonadToScalaz[F] with _root_.scalaz.MonadError[F,E] {

    val recoverable: Recoverable[F,E]

    override def raiseError[A](e: E): F[A] =
      recoverable.raiseError(e)
    override def handleError[A](fa: F[A])(f: (E) => F[A]): F[A] =
      recoverable.onErrorHandleWith(fa)(f)
  }
}

private[scalaz] trait ShimsLevel2 extends ShimsLevel1 {
  /** Converts Monix's [[monix.types.Monad Monad]]
    * instances into the Scalaz `Monad`.
    */
  implicit def monixMonadInstancesToScalaz[F[_]](implicit ev: Monad[F]): _root_.scalaz.Monad[F] =
    new ConvertMonixMonadToScalaz[F] {
      override val monad: Monad[F] = ev
      override val applicative: Applicative[F] = ev.applicative
      override val functor: Functor[F] = ev.applicative.functor
    }

  private[scalaz] trait ConvertMonixMonadToScalaz[F[_]]
    extends ConvertMonixApplicativeToScalaz[F]
      with _root_.scalaz.Monad[F] {

    val monad: Monad[F]
    override def bind[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      monad.flatMap(fa)(f)
  }
}

private[scalaz] trait ShimsLevel1 extends ShimsLevel0 {
  /** Converts Monix's [[monix.types.Applicative Applicative]]
    * instances into the Scalaz `Applicative`.
    */
  implicit def monixApplicativeInstancesToScalaz[F[_]]
    (implicit ev: Applicative[F]): _root_.scalaz.Applicative[F] =
    new ConvertMonixApplicativeToScalaz[F] {
      override val applicative: Applicative[F] = ev
      override val functor: Functor[F] = ev.functor
    }

  private[scalaz] trait ConvertMonixApplicativeToScalaz[F[_]]
    extends ConvertMonixFunctorToScalaz[F] with _root_.scalaz.Applicative[F] {

    val applicative: Applicative[F]
    override def point[A](a: => A): F[A] =
      applicative.pure(a)
    override def ap[A, B](fa: => F[A])(f: => F[(A) => B]): F[B] =
      applicative.ap(fa)(f)
    override def apply2[A, B, C](fa: => F[A], fb: => F[B])(f: (A, B) => C): F[C] =
      applicative.map2(fa, fb)(f)
  }
}

private[scalaz] trait ShimsLevel0 {
  /** Converts Monix's [[monix.types.Functor Functor]]
    * instances into the Scalaz `Functor`.
    */
  implicit def monixFunctorInstancesToScalaz[F[_]]
    (implicit ev: Functor[F]): _root_.scalaz.Functor[F] =
    new ConvertMonixFunctorToScalaz[F] { override val functor = ev }

  private[scalaz] trait ConvertMonixFunctorToScalaz[F[_]]
    extends _root_.scalaz.Functor[F] {

    val functor: Functor[F]
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      functor.map(fa)(f)
  }
}