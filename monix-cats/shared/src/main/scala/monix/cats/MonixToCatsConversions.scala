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

import cats.kernel.{Group, Monoid, Semigroup}
import monix.types._

/** Defines conversions from the Monix type-classes defined in
  * [[monix.types]] to type-class instances from the
  * [[http://typelevel.org/cats/ Cats]] library.
  */
trait MonixToCatsConversions extends MonixToCatsCore12

private[cats] trait MonixToCatsKernel0 {
  /** Given an `Applicative` for `F[A]` and a `Semigroup` defined
    * for `A`, then `F[A]` is also a `Semigroup`.
    */
  implicit def monixApplicativeToCatsSemigroup[F[_], A]
    (implicit F: Applicative[F], A: Semigroup[A]): Semigroup[F[A]] =
    new Semigroup[F[A]] {
      def combine(x: F[A], y: F[A]): F[A] =
        F.map2(x,y)(A.combine)
    }
}

private[cats] trait MonixToCatsKernel1 extends MonixToCatsKernel0 {
  /** Given an `Applicative` for `F[A]` and a `Monoid` defined
    * for `A`, then `F[A]` is also a `Monoid`.
    */
  implicit def monixApplicativeToCatsMonoid[F[_], A]
    (implicit F: Applicative[F], A: Monoid[A]): Monoid[F[A]] =
    new Monoid[F[A]] {
      def empty: F[A] =
        F.pure(A.empty)
      def combine(x: F[A], y: F[A]): F[A] =
        F.map2(x,y)(A.combine)
    }
}

private[cats] trait MonixToCatsKernel2 extends MonixToCatsKernel1 {
  /** Given an `Applicative` for `F[A]` and a `Group` defined
    * for `A`, then `F[A]` is also a `Group`.
    */
  implicit def monixApplicativeToCatsGroup[F[_], A]
    (implicit F: Applicative[F], A: Group[A]): Group[F[A]] =
    new Group[F[A]] {
      def empty: F[A] =
        F.pure(A.empty)
      def combine(x: F[A], y: F[A]): F[A] =
        F.map2(x,y)(A.combine)
      def inverse(a: F[A]): F[A] =
        F.functor.map(a)(A.inverse)
    }
}

private[cats] trait MonixToCatsCore0 extends MonixToCatsKernel2 {
  /** Converts Monix's [[monix.types.Functor Functor]]
    * instances into the Cats `Functor`.
    */
  implicit def monixToCatsFunctor[F[_]]
    (implicit ev: Functor[F]): _root_.cats.Functor[F] =
    new ConvertMonixToCatsFunctor[F] { override val _functor = ev }

  protected trait ConvertMonixToCatsFunctor[F[_]]
    extends _root_.cats.Functor[F] {

    val _functor: Functor[F]
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      _functor.map(fa)(f)
  }
}

private[cats] trait MonixToCatsCore1 extends MonixToCatsCore0 {
  /** Converts Monix's [[monix.types.Applicative Applicative]]
    * instances into the Cats `Applicative`.
    */
  implicit def monixToCatsApplicative[F[_]]
    (implicit ev: Applicative[F]): _root_.cats.Applicative[F] =
    new ConvertMonixToCatsApplicative[F] {
      override val _applicative = ev
      override val _functor = ev.functor
    }

  protected trait ConvertMonixToCatsApplicative[F[_]]
    extends _root_.cats.Applicative[F] with ConvertMonixToCatsFunctor[F] {

    val _applicative: Applicative[F]

    override def pure[A](x: A): F[A] =
      _applicative.pure(x)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] =
      _applicative.ap(ff)(fa)
    override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
      _applicative.map2(fa,fb)(f)
    override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
      _applicative.map2(fa,fb)((a,b) => (a,b))
  }
}

private[cats] trait MonixToCatsCore2 extends MonixToCatsCore1 {
  /** Converts Monix's [[monix.types.Recoverable Recoverable]]
    * instances into the Cats `ApplicativeError`.
    */
  implicit def monixToCatsApplicativeError[F[_],E]
    (implicit ev: Recoverable[F,E]): _root_.cats.ApplicativeError[F,E] =
    new ConvertMonixToCatsApplicativeError[F,E] {
      override val _recoverable = ev
      override val _applicative = ev.applicative
      override val _functor = ev.applicative.functor
    }

  protected trait ConvertMonixToCatsApplicativeError[F[_],E]
    extends _root_.cats.ApplicativeError[F,E]
      with ConvertMonixToCatsApplicative[F] {

    val _recoverable: Recoverable[F,E]

    override def raiseError[A](e: E): F[A] =
      _recoverable.raiseError(e)
    override def handleErrorWith[A](fa: F[A])(f: (E) => F[A]): F[A] =
      _recoverable.onErrorHandleWith(fa)(f)
    override def handleError[A](fa: F[A])(f: (E) => A): F[A] =
      _recoverable.onErrorHandle(fa)(f)
    override def recover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A] =
      _recoverable.onErrorRecover(fa)(pf)
    override def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
      _recoverable.onErrorRecoverWith(fa)(pf)
  }
}

private[cats] trait MonixToCatsCore3 extends MonixToCatsCore2 {
  /** Converts Monix's [[monix.types.Monad Monad]]
    * instances into the Cats `Monad`.
    */
  implicit def convertMonixToCatsMonad[F[_]](implicit ev: Monad[F]): _root_.cats.Monad[F] =
    new ConvertMonixToCatsMonad[F] {
      override val _monad = ev
      override val _applicative = ev.applicative
      override val _functor = ev.applicative.functor
    }

  protected trait ConvertMonixToCatsMonad[F[_]]
    extends _root_.cats.Monad[F] with ConvertMonixToCatsApplicative[F] {

    val _monad: Monad[F]

    override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      _monad.flatMap(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] =
      _monad.flatten(ffa)
    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] =
      defaultTailRecM(a)(f)
  }
}

private[cats] trait MonixToCatsCore4 extends MonixToCatsCore3  {
  /** Converts Monix's type instances into the Cats `MonadError`. */
  implicit def monixToCatsMonadError[F[_],E]
  (implicit M: Monad[F], R: Recoverable[F,E]): _root_.cats.MonadError[F,E] =
    new ConvertMonixToCatsMonadError[F,E] {
      override val _recoverable: Recoverable[F, E] = R
      override val _monad: Monad[F] = M
      override val _applicative: Applicative[F] = M.applicative
      override val _functor: Functor[F] = M.applicative.functor
    }

  protected trait ConvertMonixToCatsMonadError[F[_],E]
    extends _root_.cats.MonadError[F,E]
      with ConvertMonixToCatsMonad[F]
      with ConvertMonixToCatsApplicativeError[F,E]
}


private[cats] trait MonixToCatsCore5 extends MonixToCatsCore4 {
  /** Converts Monix's [[monix.types.CoflatMap CoflatMap]]
    * instances into the Cats `CoflatMap`.
    */
  implicit def monixToCatsCoflatMap[F[_]](implicit ev: CoflatMap[F]): _root_.cats.CoflatMap[F] =
    new ConvertMonixToCatsCoflatMap[F] {
      override val _coflatMap = ev
      override val _functor = ev.functor
    }

  protected trait ConvertMonixToCatsCoflatMap[F[_]]
    extends _root_.cats.CoflatMap[F] with ConvertMonixToCatsFunctor[F] {

    val _coflatMap: CoflatMap[F]

    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      _coflatMap.coflatMap(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] =
      _coflatMap.coflatten(fa)
  }
}


private[cats] trait MonixToCatsCore6 extends MonixToCatsCore5 {
  /** Converts Monix's [[monix.types.Comonad Comonad]]
    * instances into the Cats `Comonad`.
    */
  implicit def monixToCatsComonad[F[_]](implicit ev: Comonad[F]): _root_.cats.Comonad[F] =
    new ConvertMonixToCatsComonad[F] {
      override val _comonad = ev
      override val _coflatMap = ev.coflatMap
      override val _functor = ev.coflatMap.functor
    }

  protected trait ConvertMonixToCatsComonad[F[_]]
    extends _root_.cats.Comonad[F] with ConvertMonixToCatsCoflatMap[F] {

    val _comonad: Comonad[F]
    override def extract[A](x: F[A]): A = _comonad.extract(x)
  }
}

private[cats] trait MonixToCatsCore7 extends MonixToCatsCore6 {
  /** Converts Monix's type instances into the Cats `Bimonad`. */
  implicit def monixToCatsBimonad[F[_]](implicit ev1: Monad[F], ev2: Comonad[F]): _root_.cats.Bimonad[F] =
    new ConvertMonixToCatsBimonad[F] {
      override val _applicative: Applicative[F] = ev1.applicative
      override val _comonad: Comonad[F] = ev2
      override val _coflatMap: CoflatMap[F] = ev2.coflatMap
      override val _monad: Monad[F] = ev1
      override val _functor: Functor[F] = ev1.applicative.functor
    }

  private[cats] trait ConvertMonixToCatsBimonad[F[_]]
    extends _root_.cats.Bimonad[F]
      with ConvertMonixToCatsMonad[F]
      with ConvertMonixToCatsComonad[F]
}

private[cats] trait MonixToCatsCore8 extends MonixToCatsCore7 {
  /** Converts Monix's [[monix.types.MonadFilter MonadFilter]]
    * instances into the Cats `MonadFilter`.
    */
  implicit def monixToCatsMonadFilter[F[_]](implicit ev: MonadFilter[F]): _root_.cats.MonadFilter[F] =
    new ConvertMonixToCatsMonadFilter[F] {
      override val _monadFilter: MonadFilter[F] = ev
      override val _monad: Monad[F] = ev.monad
      override val _applicative: Applicative[F] = ev.monad.applicative
      override val _functor: Functor[F] = ev.monad.applicative.functor
    }

  protected trait ConvertMonixToCatsMonadFilter[F[_]]
    extends _root_.cats.MonadFilter[F] with ConvertMonixToCatsMonad[F] {

    val _monadFilter: MonadFilter[F]

    override def empty[A]: F[A] =
      _monadFilter.empty[A]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      _monadFilter.filter(fa)(f)
  }
}

private[cats] trait MonixToCatsCore9 extends MonixToCatsCore8 {
  /** Converts Monix's [[monix.types.SemigroupK SemigroupK]]
    * instances into the Cats `SemigroupK`.
    */
  implicit def monixToCatsSemigroupK[F[_]](implicit ev: SemigroupK[F]): _root_.cats.SemigroupK[F] =
    new ConvertMonixToCatsSemigroupK[F] { override val _semigroupK = ev }

  protected trait ConvertMonixToCatsSemigroupK[F[_]]
    extends _root_.cats.SemigroupK[F] {

    val _semigroupK: SemigroupK[F]
    override def combineK[A](x: F[A], y: F[A]): F[A] =
      _semigroupK.combineK(x,y)
  }
}

private[cats] trait MonixToCatsCore10 extends MonixToCatsCore9 {
  /** Converts Monix's [[monix.types.MonoidK MonoidK]]
    * instances into the Cats `MonoidK`.
    */
  implicit def monixToCatsMonoidK[F[_]](implicit ev: MonoidK[F]): _root_.cats.MonoidK[F] =
    new ConvertMonixToCatsMonoidK[F] {
      override val _monoidK: MonoidK[F] = ev
      override val _semigroupK: SemigroupK[F] = ev.semigroupK
    }

  protected trait ConvertMonixToCatsMonoidK[F[_]]
    extends _root_.cats.MonoidK[F] with ConvertMonixToCatsSemigroupK[F] {

    val _monoidK: MonoidK[F]
    override def empty[A]: F[A] = _monoidK.empty[A]
  }
}

private[cats] trait MonixToCatsCore11 extends  MonixToCatsCore10 {
  /** Converts Monix's type instances into the Cats `MonadCombine`. */
  implicit def monixToCatsMonadCombine[F[_]]
    (implicit M: MonadFilter[F], MK: MonoidK[F]): _root_.cats.MonadCombine[F] =
    new ConvertMonixToCatsMonadCombine[F] {
      override val _monadFilter: MonadFilter[F] = M
      override val _monad: Monad[F] = M.monad
      override val _applicative: Applicative[F] = M.monad.applicative
      override val _monoidK: MonoidK[F] = MK
      override val _semigroupK: SemigroupK[F] = MK.semigroupK
      override val _functor: Functor[F] = M.monad.applicative.functor
    }

  protected trait ConvertMonixToCatsMonadCombine[F[_]]
    extends _root_.cats.MonadCombine[F]
      with ConvertMonixToCatsMonoidK[F]
      with ConvertMonixToCatsMonadFilter[F] {

    // Compiler complains, because empty gets
    // inherited from two sources
    override def empty[A]: F[A] =
      _monadFilter.empty
  }
}

private[cats] trait MonixToCatsCore12 extends MonixToCatsCore11 {
  /** Converts Monix's [[monix.types.MonadRec MonadRec]]
    * instances into the Cats `Monad`.
    */
  implicit def monixToCatsMonadRec[F[_]]
    (implicit ev: MonadRec[F]): _root_.cats.Monad[F] with _root_.cats.RecursiveTailRecM[F] =
    new ConvertMonixToCatsMonadRec[F] {
      override val _monadRec: MonadRec[F] = ev
      override val _monad: Monad[F] = ev.monad
      override val _applicative: Applicative[F] = ev.monad.applicative
      override val _functor: Functor[F] = ev.monad.applicative.functor
    }

  protected trait ConvertMonixToCatsMonadRec[F[_]]
    extends _root_.cats.RecursiveTailRecM[F] with ConvertMonixToCatsMonad[F] {

    val _monadRec: MonadRec[F]
    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] =
      _monadRec.tailRecM(a)(f)
  }
}
