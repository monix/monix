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

/** Defines conversions from the Monix type-classes defined in
  * [[monix.types]] to type-class instances from the
  * [[http://www.scalaz.org/ Scalaz]] library.
  */
trait MonixToScalazConversions extends MonixToScalaz9

private[scalaz] trait MonixToScalaz0 {
  /** Converts Monix's type instances into the Scalaz `Functor`. */
  implicit def monixToScalazFunctor[F[_]](implicit ev: Functor[F]): _root_.scalaz.Functor[F] =
    new MonixToScalazFunctor[F] { override val functor = ev }

  protected trait MonixToScalazFunctor[F[_]]
    extends _root_.scalaz.Functor[F] {

    val functor: Functor[F]
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      functor.map(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz1 extends MonixToScalaz0 {
  /** Converts Monix's type instances into the Scalaz `Applicative`. */
  implicit def monixToScalazApplicative[F[_]](implicit ev: Applicative[F]): _root_.scalaz.Applicative[F] =
    new MonixToScalazApplicative[F] {
      override val applicative: Applicative[F] = ev
      override val functor: Functor[F] = ev.functor
    }

  protected trait MonixToScalazApplicative[F[_]]
    extends MonixToScalazFunctor[F] with _root_.scalaz.Applicative[F] {

    val applicative: Applicative[F]

    override def point[A](a: => A): F[A] =
      applicative.pure(a)
    override def ap[A, B](fa: => F[A])(f: => F[(A) => B]): F[B] =
      applicative.ap(f)(fa)
    override def apply2[A, B, C](fa: => F[A], fb: => F[B])(f: (A, B) => C): F[C] =
      applicative.map2(fa, fb)(f)
  }
}

private[scalaz] trait MonixToScalaz2 extends MonixToScalaz1 {
  /** Converts Monix's type instances into the Scalaz `Monad`. */
  implicit def monixMonadInstancesToScalaz[F[_]](implicit ev: Monad[F]): _root_.scalaz.Monad[F] =
    new MonixToScalazMonad[F] {
      override val monad: Monad[F] = ev
      override val applicative: Applicative[F] = ev.applicative
      override val functor: Functor[F] = ev.applicative.functor
    }

  protected trait MonixToScalazMonad[F[_]]
    extends MonixToScalazApplicative[F] with _root_.scalaz.Monad[F] {

    val monad: Monad[F]
    override def bind[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      monad.flatMap(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz3 extends MonixToScalaz2  {
  /** Converts Monix's type instances into the Scalaz `MonadError`. */
  implicit def monixToScalazMonadError[F[_],E]
    (implicit M: Monad[F], R: Recoverable[F,E]): _root_.scalaz.MonadError[F,E] =
    new MonixToScalazMonadError[F,E] {
      override val recoverable: Recoverable[F, E] = R
      override val monad: Monad[F] = M
      override val applicative: Applicative[F] = M.applicative
      override val functor: Functor[F] = M.applicative.functor
    }

  protected trait MonixToScalazMonadError[F[_],E]
    extends MonixToScalazMonad[F] with _root_.scalaz.MonadError[F,E] {

    val recoverable: Recoverable[F,E]

    override def raiseError[A](e: E): F[A] =
      recoverable.raiseError(e)
    override def handleError[A](fa: F[A])(f: (E) => F[A]): F[A] =
      recoverable.onErrorHandleWith(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz4 extends MonixToScalaz3 {
  /** Converts Monix's type instances into the Scalaz `Cobind`. */
  implicit def monixToScalazCobind[F[_]](implicit ev: CoflatMap[F]): _root_.scalaz.Cobind[F] =
    new MonixToScalazCobind[F] {
      override val coflatMap: CoflatMap[F] = ev
      override val functor: Functor[F] = ev.functor
    }

  protected trait MonixToScalazCobind[F[_]]
    extends MonixToScalazFunctor[F] with _root_.scalaz.Cobind[F] {

    val coflatMap: CoflatMap[F]
    override def cobind[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      coflatMap.coflatMap(fa)(f)
  }
}


private[scalaz] trait MonixToScalaz5 extends MonixToScalaz4 {
  /** Converts Monix's type instances into the Scalaz `Comonad`. */
  implicit def monixToScalazComonad[F[_]](implicit ev: Comonad[F]): _root_.scalaz.Comonad[F] =
    new MonixToScalazComonad[F] {
      override val comonad: Comonad[F] = ev
      override val coflatMap: CoflatMap[F] = ev.coflatMap
      override val functor: Functor[F] = ev.coflatMap.functor
    }

  protected trait MonixToScalazComonad[F[_]]
    extends MonixToScalazCobind[F] with _root_.scalaz.Comonad[F] {

    val comonad: Comonad[F]
    override def copoint[A](p: F[A]): A =
      comonad.extract(p)
  }
}

private[scalaz] trait MonixToScalaz6 extends MonixToScalaz5 {
  /** Converts Monix's type instances into the Scalaz `Plus`. */
  implicit def monixToScalazPlus[F[_]](implicit ev: SemigroupK[F]): _root_.scalaz.Plus[F] =
    new MonixToScalazPlus[F] {
      override val semigroupK = ev
    }

  protected trait MonixToScalazPlus[F[_]] extends _root_.scalaz.Plus[F] {
    val semigroupK: SemigroupK[F]
    override def plus[A](a: F[A], b: => F[A]): F[A] =
      semigroupK.combineK(a, b)
  }
}

private[scalaz] trait MonixToScalaz7 extends MonixToScalaz6 {
  /** Converts Monix's type instances into the Scalaz `PlusEmpty`. */
  implicit def monixToScalazPlusEmpty[F[_]](implicit ev: MonoidK[F]): _root_.scalaz.PlusEmpty[F] =
    new MonixToScalazPlusEmpty[F] {
      override val monoidK: MonoidK[F] = ev
      override val semigroupK: SemigroupK[F] = ev.semigroupK
    }

  private[scalaz] trait MonixToScalazPlusEmpty[F[_]]
    extends MonixToScalazPlus[F] with _root_.scalaz.PlusEmpty[F] {

    val monoidK: MonoidK[F]
    override def empty[A]: F[A] = monoidK.empty[A]
  }
}


private[scalaz] trait MonixToScalaz8 extends MonixToScalaz7 {
  /** Converts Monix's types instances into the Scalaz `MonadPlus`. */
  implicit def monixToScalazMonadPlus[F[_]](implicit MF: MonadFilter[F], MK: MonoidK[F]): _root_.scalaz.MonadPlus[F] =
    new MonixToScalazMonadPlus[F] {
      override val monadFilter: MonadFilter[F] = MF
      override val semigroupK: SemigroupK[F] = MK.semigroupK
      override val monad: Monad[F] = MF.monad
      override val monoidK: MonoidK[F] = MK
      override val applicative: Applicative[F] = MF.monad.applicative
      override val functor: Functor[F] = MF.monad.applicative.functor
    }

  protected trait MonixToScalazMonadPlus[F[_]]
    extends MonixToScalazMonad[F] with MonixToScalazPlusEmpty[F] with _root_.scalaz.MonadPlus[F] {

    val monadFilter: MonadFilter[F]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      monadFilter.filter(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz9 extends MonixToScalaz8 {
  /** Converts Monix's types instances into the Scalaz `BindRec`. */
  implicit def monixToScalazBindRec[F[_]](implicit ev: MonadRec[F]): _root_.scalaz.Monad[F] with _root_.scalaz.BindRec[F] =
    new MonixToScalazBindRec[F] {
      override val monadRec: MonadRec[F] = ev
      override val monad: Monad[F] = ev.monad
      override val applicative: Applicative[F] = ev.monad.applicative
      override val functor: Functor[F] = ev.monad.applicative.functor
    }

  protected trait MonixToScalazBindRec[F[_]]
    extends MonixToScalazMonad[F] with _root_.scalaz.BindRec[F] {

    val monadRec: MonadRec[F]
    override def tailrecM[A, B](f: (A) => F[\/[A, B]])(a: A): F[B] =
      monadRec.tailRecM(a)(a => functor.map(f(a))(_.toEither))
  }
}