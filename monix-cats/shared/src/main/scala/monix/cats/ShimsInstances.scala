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

import monix.types._

/** Groups all shim type-class conversions together. */
trait ShimsInstances extends ShimsLevel12

private[cats] trait ShimsLevel12 extends ShimsLevel11 {
  /** Converts Monix's [[monix.types.MonadRec MonadRec]]
    * instances into the Cats `Monad`.
    */
  implicit def monixTailRecMonadInstancesToCats[F[_]]
    (implicit ev: MonadRec[F]): _root_.cats.Monad[F] with _root_.cats.RecursiveTailRecM[F] =
    new ConvertMonixMonadRecToCats[F] {
      override val monadRec: MonadRec[F] = ev
      override val monad: Monad[F] = ev.monad
      override val applicative: Applicative[F] = ev.monad.applicative
      override val functor: Functor[F] = ev.monad.applicative.functor
    }

  private[cats] trait ConvertMonixMonadRecToCats[F[_]]
    extends _root_.cats.RecursiveTailRecM[F] with ConvertMonixMonadToCats[F] {

    val monadRec: MonadRec[F]
    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] =
      monadRec.tailRecM(a)(f)
  }
}

private[cats] trait ShimsLevel11 extends  ShimsLevel10 {
  /** Converts Monix's type instances into the Cats `MonadCombine`. */
  implicit def monixMonadPlusInstancesToCats[F[_]]
    (implicit M: MonadFilter[F], MK: MonoidK[F]): _root_.cats.MonadCombine[F] =
    new ConvertMonixMonadPlusToCats[F] {
      override val monadFilter: MonadFilter[F] = M
      override val monad: Monad[F] = M.monad
      override val applicative: Applicative[F] = M.monad.applicative
      override val monoidK: MonoidK[F] = MK
      override val semigroupK: SemigroupK[F] = MK.semigroupK
      override val functor: Functor[F] = M.monad.applicative.functor
    }

  private[cats] trait ConvertMonixMonadPlusToCats[F[_]]
    extends _root_.cats.MonadCombine[F]
      with ConvertMonixMonoidKToCats[F]
      with ConvertMonixMonadFilterToCats[F] {

    // Compiler complains, because empty gets
    // inherited from two sources
    override def empty[A]: F[A] =
      monadFilter.empty
  }
}

private[cats] trait ShimsLevel10 extends ShimsLevel9 {
  /** Converts Monix's [[monix.types.MonoidK MonoidK]]
    * instances into the Cats `MonoidK`.
    */
  implicit def monixMonoidKInstancesToCats[F[_]]
    (implicit ev: MonoidK[F]): _root_.cats.MonoidK[F] =
    new ConvertMonixMonoidKToCats[F] {
      override val monoidK: MonoidK[F] = ev
      override val semigroupK: SemigroupK[F] = ev.semigroupK
    }

  private[cats] trait ConvertMonixMonoidKToCats[F[_]]
    extends _root_.cats.MonoidK[F] with ConvertMonixSemigroupKToCats[F] {

    val monoidK: MonoidK[F]
    override def empty[A]: F[A] = monoidK.empty[A]
  }
}

private[cats] trait ShimsLevel9 extends ShimsLevel8 {
  /** Converts Monix's [[monix.types.SemigroupK SemigroupK]]
    * instances into the Cats `SemigroupK`.
    */
  implicit def monixSemigroupKInstancesToCats[F[_]]
    (implicit ev: SemigroupK[F]): _root_.cats.SemigroupK[F] =
    new ConvertMonixSemigroupKToCats[F] { override val semigroupK = ev }

  private[cats] trait ConvertMonixSemigroupKToCats[F[_]]
    extends _root_.cats.SemigroupK[F] {

    val semigroupK: SemigroupK[F]
    override def combineK[A](x: F[A], y: F[A]): F[A] =
      semigroupK.combineK(x,y)
  }
}

private[cats] trait ShimsLevel8 extends ShimsLevel7 {
  /** Converts Monix's [[monix.types.MonadFilter MonadFilter]]
    * instances into the Cats `MonadFilter`.
    */
  implicit def monixMonadFilterInstancesToCats[F[_]]
    (implicit ev: MonadFilter[F]): _root_.cats.MonadFilter[F] =
    new ConvertMonixMonadFilterToCats[F] {
      override val monadFilter: MonadFilter[F] = ev
      override val monad: Monad[F] = ev.monad
      override val applicative: Applicative[F] = ev.monad.applicative
      override val functor: Functor[F] = ev.monad.applicative.functor
    }

  private[cats] trait ConvertMonixMonadFilterToCats[F[_]]
    extends _root_.cats.MonadFilter[F] with ConvertMonixMonadToCats[F] {

    val monadFilter: MonadFilter[F]
    override def empty[A]: F[A] =
      monadFilter.empty[A]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      monadFilter.filter(fa)(f)
  }
}

private[cats] trait ShimsLevel7 extends ShimsLevel6 {
  /** Converts Monix's type instances into the Cats `Bimonad`. */
  implicit def monixBimonadInstancesToCats[F[_]]
    (implicit M: Monad[F], C: Comonad[F]): _root_.cats.Bimonad[F] =
    new ConvertMonixBimonadToCats[F] {
      override val applicative: Applicative[F] = M.applicative
      override val comonad: Comonad[F] = C
      override val coflatMap: CoflatMap[F] = C.coflatMap
      override val monad: Monad[F] = M
      override val functor: Functor[F] = M.applicative.functor
    }

  private[cats] trait ConvertMonixBimonadToCats[F[_]]
    extends _root_.cats.Bimonad[F]
      with ConvertMonixMonadToCats[F]
      with ConvertMonixComonadToCats[F]
}

private[cats] trait ShimsLevel6 extends ShimsLevel5 {
  /** Converts Monix's [[monix.types.Comonad Comonad]]
    * instances into the Cats `Comonad`.
    */
  implicit def monixComonadInstancesToCats[F[_]]
    (implicit ev: Comonad[F]): _root_.cats.Comonad[F] =
    new ConvertMonixComonadToCats[F] {
      override val comonad = ev
      override val coflatMap = ev.coflatMap
      override val functor = ev.coflatMap.functor
    }

  private[cats] trait ConvertMonixComonadToCats[F[_]]
    extends _root_.cats.Comonad[F] with ConvertMonixCoflatMapToCats[F] {

    val comonad: Comonad[F]
    override def extract[A](x: F[A]): A = comonad.extract(x)
  }
}

private[cats] trait ShimsLevel5 extends ShimsLevel4 {
  /** Converts Monix's [[monix.types.CoflatMap CoflatMap]]
    * instances into the Cats `CoflatMap`.
    */
  implicit def monixCoflatMapInstancesToCats[F[_]]
    (implicit ev: CoflatMap[F]): _root_.cats.CoflatMap[F] =
    new ConvertMonixCoflatMapToCats[F] {
      override val coflatMap = ev
      override val functor = ev.functor
    }

  private[cats] trait ConvertMonixCoflatMapToCats[F[_]]
    extends _root_.cats.CoflatMap[F] with ConvertMonixFunctorToCats[F] {

    val coflatMap: CoflatMap[F]

    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      coflatMap.coflatMap(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] =
      coflatMap.coflatten(fa)
  }
}

private[cats] trait ShimsLevel4 extends ShimsLevel3  {
  /** Converts Monix's type instances into the Cats `MonadError`. */
  implicit def monixMonadErrorInstancesToCats[F[_],E]
    (implicit M: Monad[F], R: Recoverable[F,E]): _root_.cats.MonadError[F,E] =
    new ConvertMonixMonadErrorToCats[F,E] {
      override val recoverable: Recoverable[F, E] = R
      override val monad: Monad[F] = M
      override val applicative: Applicative[F] = M.applicative
      override val functor: Functor[F] = M.applicative.functor
    }

  private[cats] trait ConvertMonixMonadErrorToCats[F[_],E]
    extends _root_.cats.MonadError[F,E]
      with ConvertMonixMonadToCats[F]
      with ConvertMonixRecoverableToCats[F,E]
}

private[cats] trait ShimsLevel3 extends ShimsLevel2 {
  /** Converts Monix's [[monix.types.Monad Monad]]
    * instances into the Cats `Monad`.
    */
  implicit def monixMonadInstancesToCats[F[_]](implicit ev: Monad[F]): _root_.cats.Monad[F] =
    new ConvertMonixMonadToCats[F] {
      override val monad = ev
      override val applicative = ev.applicative
      override val functor = ev.applicative.functor
    }

  private[cats] trait ConvertMonixMonadToCats[F[_]]
    extends _root_.cats.Monad[F] with ConvertMonixApplicativeToCats[F] {

    val monad: Monad[F]
    override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] = monad.flatMap(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] = monad.flatten(ffa)

    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] =
      defaultTailRecM(a)(f)
  }
}

private[cats] trait ShimsLevel2 extends ShimsLevel1 {
  /** Converts Monix's [[monix.types.Recoverable Recoverable]]
    * instances into the Cats `ApplicativeError`.
    */
  implicit def monixApplicativeErrorInstancesToCats[F[_],E]
    (implicit ev: Recoverable[F,E]): _root_.cats.ApplicativeError[F,E] =
    new ConvertMonixRecoverableToCats[F,E] {
      override val recoverable = ev
      override val applicative = ev.applicative
      override val functor = ev.applicative.functor
    }

  private[cats] trait ConvertMonixRecoverableToCats[F[_],E]
    extends _root_.cats.ApplicativeError[F,E]
      with ConvertMonixApplicativeToCats[F] {

    val recoverable: Recoverable[F,E]

    override def raiseError[A](e: E): F[A] =
      recoverable.raiseError(e)
    override def handleErrorWith[A](fa: F[A])(f: (E) => F[A]): F[A] =
      recoverable.onErrorHandleWith(fa)(f)
    override def handleError[A](fa: F[A])(f: (E) => A): F[A] =
      recoverable.onErrorHandle(fa)(f)
    override def recover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A] =
      recoverable.onErrorRecover(fa)(pf)
    override def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
      recoverable.onErrorRecoverWith(fa)(pf)
  }
}

private[cats] trait ShimsLevel1 extends ShimsLevel0 {
  /** Converts Monix's [[monix.types.Applicative Applicative]]
    * instances into the Cats `Applicative`.
    */
  implicit def monixApplicativeInstancesToCats[F[_]]
    (implicit ev: Applicative[F]): _root_.cats.Applicative[F] =
    new ConvertMonixApplicativeToCats[F] {
      override val applicative = ev
      override val functor = ev.functor
    }

  private[cats] trait ConvertMonixApplicativeToCats[F[_]]
    extends _root_.cats.Applicative[F] with ConvertMonixFunctorToCats[F] {

    val applicative: Applicative[F]
    override def pure[A](x: A): F[A] = applicative.pure(x)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] = applicative.ap(fa)(ff)
    override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] = applicative.map2(fa,fb)(f)
    override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = applicative.map2(fa,fb)((a,b) => (a,b))
  }
}

private[cats] trait ShimsLevel0 {
  /** Converts Monix's [[monix.types.Functor Functor]]
    * instances into the Cats `Functor`.
    */
  implicit def monixFunctorInstancesToCats[F[_]]
    (implicit ev: Functor[F]): _root_.cats.Functor[F] =
    new ConvertMonixFunctorToCats[F] { override val functor = ev }

  private[cats] trait ConvertMonixFunctorToCats[F[_]]
    extends _root_.cats.Functor[F] {

    val functor: Functor[F]
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] = functor.map(fa)(f)
  }
}