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
import _root_.scalaz.{Monoid, Semigroup, \/}

/** Defines conversions from the Monix type-classes defined in
  * [[monix.types]] to type-class instances from the
  * [[http://www.scalaz.org/ Scalaz]] library.
  */
trait MonixToScalazConversions extends MonixToScalaz10

private[scalaz] trait MonixToScalazKernel0 {
  /** Given an `Applicative` for `F[A]` and a `Semigroup` defined
    * for `A`, then `F[A]` is also a `Semigroup`.
    */
  implicit def monixApplicativeToScalazSemigroup[F[_], A]
    (implicit F: Applicative[F], A: Semigroup[A]): Semigroup[F[A]] =
    new Semigroup[F[A]] {
      override def append(f1: F[A], f2: => F[A]): F[A] =
        F.map2(f1,f2)((a,b) => A.append(a,b))
    }
}

private[scalaz] trait MonixToScalazKernel1 extends MonixToScalazKernel0 {
  /** Given an `Applicative` for `F[A]` and a `Monoid` defined
    * for `A`, then `F[A]` is also a `Monoid`.
    */
  implicit def monixApplicativeToScalazMonoid[F[_], A]
    (implicit F: Applicative[F], A: Monoid[A]): Monoid[F[A]] =
    new Monoid[F[A]] {
      override def zero: F[A] =
        F.pure(A.zero)
      override def append(f1: F[A], f2: => F[A]): F[A] =
        F.map2(f1,f2)((a,b) => A.append(a,b))
    }
}

private[scalaz] trait MonixToScalaz0 extends MonixToScalazKernel1 {
  /** Converts Monix's type instances into the Scalaz `Functor`. */
  implicit def monixToScalazFunctor[F[_] : Functor]: _root_.scalaz.Functor[F] =
    new MonixToScalazFunctor[F]()

  protected class MonixToScalazFunctor[F[_]](implicit F: Functor[F])
    extends _root_.scalaz.Functor[F] {

    final override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.map(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz1 extends MonixToScalaz0 {
  /** Converts Monix's type instances into the Scalaz `Applicative`. */
  implicit def monixToScalazApplicative[F[_] : Applicative]: _root_.scalaz.Applicative[F] =
    new MonixToScalazApplicative[F]()

  protected class MonixToScalazApplicative[F[_]](implicit F: Applicative[F])
    extends _root_.scalaz.Applicative[F] {

    final override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.functor.map(fa)(f)
    override def point[A](a: => A): F[A] =
      F.pure(a)
    override def ap[A, B](fa: => F[A])(f: => F[(A) => B]): F[B] =
      F.ap(f)(fa)
    override def apply2[A, B, C](fa: => F[A], fb: => F[B])(f: (A, B) => C): F[C] =
      F.map2(fa, fb)(f)
  }
}

private[scalaz] trait MonixToScalaz2 extends MonixToScalaz1 {
  /** Converts Monix's type instances into the Scalaz `Monad`. */
  implicit def monixMonadInstancesToScalaz[F[_] : Monad]: _root_.scalaz.Monad[F] =
    new MonixToScalazMonad[F]()

  protected class MonixToScalazMonad[F[_]](implicit val F: Monad[F])
    extends _root_.scalaz.Monad[F] {

    final override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.functor.map(fa)(f)
    final override def point[A](a: => A): F[A] =
      F.applicative.pure(a)
    final override def ap[A, B](fa: => F[A])(f: => F[(A) => B]): F[B] =
      F.applicative.ap(f)(fa)
    final override def apply2[A, B, C](fa: => F[A], fb: => F[B])(f: (A, B) => C): F[C] =
      F.applicative.map2(fa, fb)(f)
    final override def bind[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      F.flatMap(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz3 extends MonixToScalaz2  {
  /** Converts Monix's type instances into the Scalaz `MonadError`. */
  implicit def monixToScalazMonadError[F[_],E](implicit F: MonadError[F,E]): _root_.scalaz.MonadError[F,E] =
    new MonixToScalazMonadError[F,E]()

  protected class MonixToScalazMonadError[F[_],E](implicit F: MonadError[F,E])
    extends MonixToScalazMonad[F]()(F.monad) with _root_.scalaz.MonadError[F,E] {

    final override def raiseError[A](e: E): F[A] =
      F.raiseError(e)
    final override def handleError[A](fa: F[A])(f: (E) => F[A]): F[A] =
      F.onErrorHandleWith(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz4 extends MonixToScalaz3  {
  /** Converts Monix's type instances into the Scalaz `MonadError`. */
  implicit def monixToScalazCatchable[F[_]](implicit F: MonadError[F,Throwable]): _root_.scalaz.Catchable[F] =
    new MonixToScalazCatchable[F]()

  protected class MonixToScalazCatchable[F[_]](implicit F: MonadError[F,Throwable])
    extends MonixToScalazMonad[F]()(F.monad) with _root_.scalaz.Catchable[F] {

    override final def attempt[A](f: F[A]): F[\/[Throwable, A]] = {
      val A = F.functor
      F.onErrorHandle(A.map(f)(\/.right[Throwable,A]))(\/.left)
    }

    override final def fail[A](err: Throwable): F[A] =
      F.raiseError(err)
  }
}


private[scalaz] trait MonixToScalaz5 extends MonixToScalaz4 {
  /** Converts Monix's type instances into the Scalaz `Cobind`. */
  implicit def monixToScalazCobind[F[_] : Cobind]: _root_.scalaz.Cobind[F] =
    new MonixToScalazCobind[F]()

  protected class MonixToScalazCobind[F[_]](implicit F: Cobind[F])
    extends MonixToScalazFunctor[F]()(F.functor) with _root_.scalaz.Cobind[F] {

    final override def cobind[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      F.coflatMap(fa)(f)
  }
}


private[scalaz] trait MonixToScalaz6 extends MonixToScalaz5 {
  /** Converts Monix's type instances into the Scalaz `Comonad`. */
  implicit def monixToScalazComonad[F[_] : Comonad]: _root_.scalaz.Comonad[F] =
    new MonixToScalazComonad[F]()

  protected class MonixToScalazComonad[F[_]](implicit F: Comonad[F])
    extends MonixToScalazCobind[F]()(F.cobind) with _root_.scalaz.Comonad[F] {

    final override def copoint[A](p: F[A]): A =
      F.extract(p)
  }
}

private[scalaz] trait MonixToScalaz7 extends MonixToScalaz6 {
  /** Converts Monix's type instances into the Scalaz `Plus`. */
  implicit def monixToScalazPlus[F[_] : SemigroupK]: _root_.scalaz.Plus[F] =
    new MonixToScalazPlus[F]()

  protected class MonixToScalazPlus[F[_]](implicit F: SemigroupK[F])
    extends _root_.scalaz.Plus[F] {

    final override def plus[A](a: F[A], b: => F[A]): F[A] =
      F.combineK(a, b)
  }
}

private[scalaz] trait MonixToScalaz8 extends MonixToScalaz7 {
  /** Converts Monix's type instances into the Scalaz `PlusEmpty`. */
  implicit def monixToScalazPlusEmpty[F[_] : MonoidK]: _root_.scalaz.PlusEmpty[F] =
    new MonixToScalazPlusEmpty[F]()

  protected class MonixToScalazPlusEmpty[F[_]](implicit F: MonoidK[F])
    extends MonixToScalazPlus[F]()(F.semigroupK) with _root_.scalaz.PlusEmpty[F] {

    final override def empty[A]: F[A] = F.empty[A]
  }
}


private[scalaz] trait MonixToScalaz9 extends MonixToScalaz8 {
  /** Converts Monix's types instances into the Scalaz `MonadPlus`. */
  implicit def monixToScalazMonadPlus[F[_] : MonadFilter : MonoidK]: _root_.scalaz.MonadPlus[F] =
    new MonixToScalazMonadPlus[F]()

  protected class MonixToScalazMonadPlus[F[_]](implicit MF: MonadFilter[F], MK: MonoidK[F])
    extends MonixToScalazMonad[F]()(MF.monad) with _root_.scalaz.MonadPlus[F] {

    final override def empty[A]: F[A] =
      MK.empty
    final override def plus[A](a: F[A], b: => F[A]): F[A] =
      MK.semigroupK.combineK(a,b)
    final override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      MF.filter(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz10 extends MonixToScalaz9 {
  /** Converts Monix's types instances into the Scalaz `BindRec`. */
  implicit def monixToScalazBindRec[F[_] : MonadRec]: _root_.scalaz.Monad[F] with _root_.scalaz.BindRec[F] =
    new MonixToScalazBindRec[F]()

  protected class MonixToScalazBindRec[F[_]](implicit F: MonadRec[F])
    extends MonixToScalazMonad[F]()(F.monad) with _root_.scalaz.BindRec[F] {

    final override def tailrecM[A, B](f: (A) => F[\/[A, B]])(a: A): F[B] =
      F.tailRecM(a)(a => F.functor.map(f(a))(_.toEither))
  }
}