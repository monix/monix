/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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
  /** Given a Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `scalaz.Semigroup` defined for `A`, then `F[A]` can also
    * have a `Semigroup` instance.
    *
    * You can import [[monixApplicativeHasScalazSemigroup]] in scope
    * initiate the [[MonixApplicativeHasScalazSemigroup]] class.
    */
  implicit def monixApplicativeHasScalazSemigroup[F[_], A]
    (implicit F: Applicative[F], A: Semigroup[A]): Semigroup[F[A]] =
    new MonixApplicativeHasScalazSemigroup[F,A]()

  /** Given a Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `scalaz.Semigroup` defined for `A`, then `F[A]` can also
    * have a `Semigroup` instance.
    *
    * You can import [[monixApplicativeHasScalazSemigroup]] in scope
    * initiate the [[MonixApplicativeHasScalazSemigroup]] class.
    */
  class MonixApplicativeHasScalazSemigroup[F[_], A]
    (implicit F: Applicative[F], A: Semigroup[A])
    extends Semigroup[F[A]] {

    override def append(f1: F[A], f2: => F[A]): F[A] =
      F.map2(f1,f2)((a,b) => A.append(a,b))
  }
}

private[scalaz] trait MonixToScalazKernel1 extends MonixToScalazKernel0 {
  /** Given a Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `scalaz.Monoid` defined for `A`, then `F[A]` can also
    * have a `Monoid` instance.
    *
    * You can import [[monixApplicativeHasScalazMonoid]] in scope
    * initiate the [[MonixApplicativeHasScalazMonoid]] class.
    */
  implicit def monixApplicativeHasScalazMonoid[F[_], A]
    (implicit F: Applicative[F], A: Monoid[A]): Monoid[F[A]] =
    new MonixApplicativeHasScalazMonoid[F,A]()

  /** Given a Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `scalaz.Monoid` defined for `A`, then `F[A]` can also
    * have a `Monoid` instance.
    *
    * You can import [[monixApplicativeHasScalazMonoid]] in scope
    * initiate the [[MonixApplicativeHasScalazMonoid]] class.
    */
  class MonixApplicativeHasScalazMonoid[F[_], A]
    (implicit F: Applicative[F], A: Monoid[A])
    extends MonixApplicativeHasScalazSemigroup[F,A] with Monoid[F[A]] {

    override def zero: F[A] =
      F.pure(A.zero)
  }
}

private[scalaz] trait MonixToScalaz0 extends MonixToScalazKernel1 {
  /** Converts Monix's [[monix.types.Functor Functor]] instances into
    * the Scalaz `Functor`.
    *
    * You can import [[monixToScalazFunctor]] in scope, or initiate/extend
    * the [[MonixToScalazFunctor]] class.
    */
  implicit def monixToScalazFunctor[F[_] : Functor]: _root_.scalaz.Functor[F] =
    new MonixToScalazFunctor[F]()

  /** Converts Monix's [[monix.types.Functor Functor]] instances into
    * the Scalaz `Functor`.
    *
    * You can import [[monixToScalazFunctor]] in scope, or initiate/extend
    * the [[MonixToScalazFunctor]] class.
    */
  class MonixToScalazFunctor[F[_]](implicit F: Functor[F])
    extends _root_.scalaz.Functor[F] {

    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.map(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz1 extends MonixToScalaz0 {
  /** Converts Monix's [[monix.types.Applicative Applicative]] instances into
    * the Scalaz `Applicative`.
    *
    * You can import [[monixToScalazApplicative]] in scope, or initiate/extend
    * the [[MonixToScalazApplicative]] class.
    */
  implicit def monixToScalazApplicative[F[_] : Applicative]: _root_.scalaz.Applicative[F] =
    new MonixToScalazApplicative[F]()

  /** Converts Monix's [[monix.types.Applicative Applicative]] instances into
    * the Scalaz `Applicative`.
    *
    * You can import [[monixToScalazApplicative]] in scope, or initiate/extend
    * the [[MonixToScalazApplicative]] class.
    */
  class MonixToScalazApplicative[F[_]](implicit F: Applicative[F])
    extends _root_.scalaz.Applicative[F] {

    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
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
  /** Converts Monix's [[monix.types.Monad Monad]] instances into
    * the Scalaz `Monad`.
    *
    * You can import [[monixToScalazMonad]] in scope, or initiate/extend
    * the [[MonixToScalazMonad]] class.
    */
  implicit def monixToScalazMonad[F[_] : Monad]: _root_.scalaz.Monad[F] =
    new MonixToScalazMonad[F]()

  /** Converts Monix's [[monix.types.Monad Monad]] instances into
    * the Scalaz `Monad`.
    *
    * You can import [[monixToScalazMonad]] in scope, or initiate/extend
    * the [[MonixToScalazMonad]] class.
    */
  class MonixToScalazMonad[F[_]](implicit val F: Monad[F])
    extends _root_.scalaz.Monad[F] {

    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.functor.map(fa)(f)
    override def point[A](a: => A): F[A] =
      F.applicative.pure(a)
    override def ap[A, B](fa: => F[A])(f: => F[(A) => B]): F[B] =
      F.applicative.ap(f)(fa)
    override def apply2[A, B, C](fa: => F[A], fb: => F[B])(f: (A, B) => C): F[C] =
      F.applicative.map2(fa, fb)(f)
    override def bind[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      F.flatMap(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz3 extends MonixToScalaz2  {
  /** Converts Monix's [[monix.types.MonadError MonadError]] instances into
    * the Scalaz `MonadError`.
    *
    * You can import [[monixToScalazMonadError]] in scope, or initiate/extend
    * the [[MonixToScalazMonadError]] class.
    */
  implicit def monixToScalazMonadError[F[_],E](implicit F: MonadError[F,E]): _root_.scalaz.MonadError[F,E] =
    new MonixToScalazMonadError[F,E]()

  /** Converts Monix's [[monix.types.MonadError MonadError]] instances into
    * the Scalaz `MonadError`.
    *
    * You can import [[monixToScalazMonadError]] in scope, or initiate/extend
    * the [[MonixToScalazMonadError]] class.
    */
  class MonixToScalazMonadError[F[_],E](implicit F: MonadError[F,E])
    extends MonixToScalazMonad[F]()(F.monad) with _root_.scalaz.MonadError[F,E] {

    override def raiseError[A](e: E): F[A] =
      F.raiseError(e)
    override def handleError[A](fa: F[A])(f: (E) => F[A]): F[A] =
      F.onErrorHandleWith(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz4 extends MonixToScalaz3  {
  /** Converts Monix's [[monix.types.MonadError MonadError]] instances into
    * the Scalaz `Catchable`.
    *
    * You can import [[monixToScalazCatchable]] in scope, or initiate/extend
    * the [[MonixToScalazCatchable]] class.
    */
  implicit def monixToScalazCatchable[F[_]](implicit F: MonadError[F,Throwable]): _root_.scalaz.Catchable[F] =
    new MonixToScalazCatchable[F]()

  class MonixToScalazCatchable[F[_]](implicit F: MonadError[F,Throwable])
    extends MonixToScalazMonad[F]()(F.monad) with _root_.scalaz.Catchable[F] {

    override def attempt[A](f: F[A]): F[\/[Throwable, A]] = {
      val A = F.functor
      F.onErrorHandle(A.map(f)(\/.right[Throwable,A]))(\/.left)
    }

    override def fail[A](err: Throwable): F[A] =
      F.raiseError(err)
  }
}


private[scalaz] trait MonixToScalaz5 extends MonixToScalaz4 {
  /** Converts Monix's [[monix.types.Cobind Cobind]] instances into
    * the Scalaz `Cobind`.
    *
    * You can import [[monixToScalazCobind]] in scope, or initiate/extend
    * the [[MonixToScalazCobind]] class.
    */
  implicit def monixToScalazCobind[F[_] : Cobind]: _root_.scalaz.Cobind[F] =
    new MonixToScalazCobind[F]()

  /** Converts Monix's [[monix.types.Cobind Cobind]] instances into
    * the Scalaz `Cobind`.
    *
    * You can import [[monixToScalazCobind]] in scope, or initiate/extend
    * the [[MonixToScalazCobind]] class.
    */
  class MonixToScalazCobind[F[_]](implicit F: Cobind[F])
    extends MonixToScalazFunctor[F]()(F.functor) with _root_.scalaz.Cobind[F] {

    override def cobind[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      F.coflatMap(fa)(f)
  }
}


private[scalaz] trait MonixToScalaz6 extends MonixToScalaz5 {
  /** Converts Monix's [[monix.types.Comonad Comonad]] instances into
    * the Scalaz `Comonad`.
    *
    * You can import [[monixToScalazComonad]] in scope, or initiate/extend
    * the [[MonixToScalazComonad]] class.
    */
  implicit def monixToScalazComonad[F[_] : Comonad]: _root_.scalaz.Comonad[F] =
    new MonixToScalazComonad[F]()

  /** Converts Monix's [[monix.types.Comonad Comonad]] instances into
    * the Scalaz `Comonad`.
    *
    * You can import [[monixToScalazComonad]] in scope, or initiate/extend
    * the [[MonixToScalazComonad]] class.
    */
  class MonixToScalazComonad[F[_]](implicit F: Comonad[F])
    extends MonixToScalazCobind[F]()(F.cobind) with _root_.scalaz.Comonad[F] {

    override def copoint[A](p: F[A]): A =
      F.extract(p)
  }
}

private[scalaz] trait MonixToScalaz7 extends MonixToScalaz6 {
  /** Converts Monix's [[monix.types.SemigroupK SemigroupK]] instances into
    * the Scalaz `Plus`.
    *
    * You can import [[monixToScalazPlus]] in scope, or initiate/extend
    * the [[MonixToScalazPlus]] class.
    */
  implicit def monixToScalazPlus[F[_] : SemigroupK]: _root_.scalaz.Plus[F] =
    new MonixToScalazPlus[F]()

  /** Converts Monix's [[monix.types.SemigroupK SemigroupK]] instances into
    * the Scalaz `Plus`.
    *
    * You can import [[monixToScalazPlus]] in scope, or initiate/extend
    * the [[MonixToScalazPlus]] class.
    */
  class MonixToScalazPlus[F[_]](implicit F: SemigroupK[F])
    extends _root_.scalaz.Plus[F] {

    override def plus[A](a: F[A], b: => F[A]): F[A] =
      F.combineK(a, b)
  }
}

private[scalaz] trait MonixToScalaz8 extends MonixToScalaz7 {
  /** Converts Monix's [[monix.types.MonoidK MonoidK]] instances into
    * the Scalaz `PlusEmpty`.
    *
    * You can import [[monixToScalazPlusEmpty]] in scope, or initiate/extend
    * the [[MonixToScalazPlusEmpty]] class.
    */
  implicit def monixToScalazPlusEmpty[F[_] : MonoidK]: _root_.scalaz.PlusEmpty[F] =
    new MonixToScalazPlusEmpty[F]()

  class MonixToScalazPlusEmpty[F[_]](implicit F: MonoidK[F])
    extends MonixToScalazPlus[F]()(F.semigroupK) with _root_.scalaz.PlusEmpty[F] {

    override def empty[A]: F[A] = F.empty[A]
  }
}


private[scalaz] trait MonixToScalaz9 extends MonixToScalaz8 {
  /** Converts Monix's [[monix.types.MonadFilter MonadFilter]]
    * and [[monix.types.MonoidK MonoidK]] instances into
    * the Scalaz `MonadPlus`.
    *
    * You can import [[monixToScalazMonadPlus]] in scope, or initiate/extend
    * the [[MonixToScalazMonadPlus]] class.
    */
  implicit def monixToScalazMonadPlus[F[_] : MonadFilter : MonoidK]: _root_.scalaz.MonadPlus[F] =
    new MonixToScalazMonadPlus[F]()

  /** Converts Monix's [[monix.types.MonadFilter MonadFilter]]
    * and [[monix.types.MonoidK MonoidK]] instances into
    * the Scalaz `MonadPlus`.
    *
    * You can import [[monixToScalazMonadPlus]] in scope, or initiate/extend
    * the [[MonixToScalazMonadPlus]] class.
    */
  class MonixToScalazMonadPlus[F[_]](implicit MF: MonadFilter[F], MK: MonoidK[F])
    extends MonixToScalazMonad[F]()(MF.monad) with _root_.scalaz.MonadPlus[F] {

    override def empty[A]: F[A] =
      MK.empty
    override def plus[A](a: F[A], b: => F[A]): F[A] =
      MK.semigroupK.combineK(a,b)
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      MF.filter(fa)(f)
  }
}

private[scalaz] trait MonixToScalaz10 extends MonixToScalaz9 {
  /** Converts Monix's [[monix.types.MonadRec MonadRec]] instances into
    * the Scalaz `BindRec`.
    *
    * You can import [[monixToScalazBindRec]] in scope, or initiate/extend
    * the [[MonixToScalazBindRec]] class.
    */
  implicit def monixToScalazBindRec[F[_] : MonadRec]: _root_.scalaz.Monad[F] with _root_.scalaz.BindRec[F] =
    new MonixToScalazBindRec[F]()

  class MonixToScalazBindRec[F[_]](implicit F: MonadRec[F])
    extends MonixToScalazMonad[F]()(F.monad) with _root_.scalaz.BindRec[F] {

    override def tailrecM[A, B](f: (A) => F[\/[A, B]])(a: A): F[B] =
      F.tailRecM(a)(a => F.functor.map(f(a))(_.toEither))
  }
}