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

package monix.cats

import cats.kernel.{Group, Monoid, Semigroup}
import monix.types._

/** Defines conversions from the Monix type-classes defined in
  * [[monix.types]] to type-class instances from the
  * [[http://typelevel.org/cats/ Cats]] library.
  */
trait MonixToCatsConversions extends MonixToCatsCore11

private[cats] trait MonixToCatsKernel0 {
  /** Given a Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `cats.Semigroup` defined for `A`, then `F[A]` can also
    * have a `Semigroup` instance.
    *
    * You can import [[monixApplicativeHasCatsSemigroup]] in scope
    * initiate the [[MonixApplicativeHasCatsSemigroup]] class.
    */
  implicit def monixApplicativeHasCatsSemigroup[F[_], A]
    (implicit F: Applicative[F], A: Semigroup[A]): Semigroup[F[A]] =
    new MonixApplicativeHasCatsSemigroup[F,A]()

  /** Given a Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `cats.Semigroup` defined for `A`, then `F[A]` can also
    * have a `Semigroup` instance.
    *
    * You can import [[monixApplicativeHasCatsSemigroup]] in scope
    * initiate the [[MonixApplicativeHasCatsSemigroup]] class.
    */
  class MonixApplicativeHasCatsSemigroup[F[_], A]
    (implicit F: Applicative[F], A: Semigroup[A])
    extends Semigroup[F[A]] {

    override def combine(x: F[A], y: F[A]): F[A] =
      F.map2(x,y)(A.combine)
  }
}

private[cats] trait MonixToCatsKernel1 extends MonixToCatsKernel0 {
  /** Given an Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `cats.Monoid` defined for `A`, then `F[A]` can also have a
    * `Monoid` instance.
    *
    * You can import [[monixApplicativeHasCatsMonoid]] in scope or
    * initiate the [[MonixApplicativeHasCatsMonoid]] class.
    */
  implicit def monixApplicativeHasCatsMonoid[F[_], A]
    (implicit F: Applicative[F], A: Monoid[A]): Monoid[F[A]] =
    new MonixApplicativeHasCatsMonoid[F,A]()

  /** Given an Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `cats.Monoid` defined for `A`, then `F[A]` can also have a
    * `Monoid` instance.
    *
    * You can import [[monixApplicativeHasCatsMonoid]] in scope or
    * initiate the [[MonixApplicativeHasCatsMonoid]] class.
    */
  class MonixApplicativeHasCatsMonoid[F[_], A]
    (implicit F: Applicative[F], A: Monoid[A])
    extends MonixApplicativeHasCatsSemigroup[F,A] with Monoid[F[A]] {

    override def empty: F[A] =
      F.pure(A.empty)
  }
}

private[cats] trait MonixToCatsKernel2 extends MonixToCatsKernel1 {
  /** Given an Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `cats.Group` defined for `A`, then `F[A]` can also have a
    * `Group` instance.
    *
    * You can import [[monixApplicativeHasCatsGroup]] in scope or
    * initiate the [[MonixApplicativeHasCatsGroup]] class.
    */
  implicit def monixApplicativeHasCatsGroup[F[_], A]
    (implicit F: Applicative[F], A: Group[A]): Group[F[A]] =
    new MonixApplicativeHasCatsGroup[F,A]()

  /** Given an Monix [[monix.types.Applicative Applicative]] for `F[A]`
    * and a `cats.Group` defined for `A`, then `F[A]` can also have a
    * `Group` instance.
    *
    * You can import [[monixApplicativeHasCatsGroup]] in scope or
    * initiate the [[MonixApplicativeHasCatsGroup]] class.
    */
  class MonixApplicativeHasCatsGroup[F[_], A]
    (implicit F: Applicative[F], A: Group[A])
    extends MonixApplicativeHasCatsMonoid[F,A] with Group[F[A]] {

    override def inverse(a: F[A]): F[A] =
      F.functor.map(a)(A.inverse)
  }
}

private[cats] trait MonixToCatsCore0 extends MonixToCatsKernel2 {
  /** Converts Monix's [[monix.types.Functor Functor]] instances into
    * the Cats `Functor`.
    *
    * You can import [[monixToCatsFunctor]] in scope, or initiate/extend
    * the [[MonixToCatsFunctor]] class.
    */
  implicit def monixToCatsFunctor[F[_] : Functor]: _root_.cats.Functor[F] =
    new MonixToCatsFunctor[F]()

  /** Converts Monix's [[monix.types.Functor Functor]] instances into
    * the Cats `Functor`.
    *
    * You can import [[monixToCatsFunctor]] in scope, or initiate/extend
    * the [[MonixToCatsFunctor]] class.
    */
  class MonixToCatsFunctor[F[_]](implicit ev: Functor[F])
    extends _root_.cats.Functor[F] {

    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      ev.map(fa)(f)
  }
}

private[cats] trait MonixToCatsCore1 extends MonixToCatsCore0 {
  /** Converts Monix's [[monix.types.Applicative Applicative]] instances into
    * the Cats `Applicative`.
    *
    * You can import [[monixToCatsApplicative]] in scope, or initiate/extend
    * the [[MonixToCatsApplicative]] class.
    */
  implicit def monixToCatsApplicative[F[_] : Applicative]: _root_.cats.Applicative[F] =
    new MonixToCatsApplicative[F]()

  /** Converts Monix's [[monix.types.Applicative Applicative]] instances into
    * the Cats `Applicative`.
    *
    * You can import [[monixToCatsApplicative]] in scope, or initiate/extend
    * the [[MonixToCatsApplicative]] class.
    */
  class MonixToCatsApplicative[F[_]](implicit F: Applicative[F])
    extends _root_.cats.Applicative[F]  {

    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.functor.map(fa)(f)
    override def pure[A](x: A): F[A] =
      F.pure(x)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] =
      F.ap(ff)(fa)
    override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
      F.map2(fa,fb)(f)
    override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
      F.map2(fa,fb)((a,b) => (a,b))
  }
}

private[cats] trait MonixToCatsCore2 extends MonixToCatsCore1 {
  /** Converts Monix's [[monix.types.Monad Monad]] instances into
    * the Cats `Monad`.
    *
    * You can import [[monixToCatsMonad]] in scope, or initiate/extend
    * the [[MonixToCatsMonad]] class.
    */
  implicit def monixToCatsMonad[F[_] : Monad]: _root_.cats.Monad[F] =
    new MonixToCatsMonad[F]()

  /** Converts Monix's [[monix.types.Monad Monad]] instances into
    * the Cats `Monad`.
    *
    * You can import [[monixToCatsMonad]] in scope, or initiate/extend
    * the [[MonixToCatsMonad]] class.
    */
  class MonixToCatsMonad[F[_]](implicit F: Monad[F]) extends _root_.cats.Monad[F] {
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.functor.map(fa)(f)
    override def pure[A](x: A): F[A] =
      F.applicative.pure(x)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] =
      F.applicative.ap(ff)(fa)
    override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
      F.applicative.map2(fa,fb)(f)
    override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
      F.applicative.map2(fa,fb)((a,b) => (a,b))
    override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      F.flatMap(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] =
      F.flatten(ffa)

    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] = {
      val instance = F.asInstanceOf[AnyRef]

      instance match {
        case ref: MonadRec[_] =>
          // Workaround for Cats Monad instances that might implement
          // a stack-safe `tailRecM`, since unfortunately the
          // `RecursiveTailRecM` marker and the `FlatMapRec` type
          // are now gone and all monads are expected to implement
          // a safe `tailRecM`, which is not really possible
          ref.asInstanceOf[MonadRec[F]].tailRecM(a)(f)
        case _ =>
          MonadRec.defaultTailRecM(a)(f)(F)
      }
    }
  }
}

private[cats] trait MonixToCatsCore3 extends MonixToCatsCore2 {
  /** Converts Monix's [[monix.types.MonadError MonadError]] instances into
    * the Cats `MonadError`.
    *
    * You can import [[monixToCatsMonadError]] in scope, or initiate/extend
    * the [[MonixToCatsMonadError]] class.
    */
  implicit def monixToCatsMonadError[F[_],E]
    (implicit ev: MonadError[F,E]): _root_.cats.MonadError[F,E] =
    new MonixToCatsMonadError()

  /** Converts Monix's [[monix.types.MonadError MonadError]] instances into
    * the Cats `MonadError`.
    *
    * You can import [[monixToCatsMonadError]] in scope, or initiate/extend
    * the [[MonixToCatsMonadError]] class.
    */
  class MonixToCatsMonadError[F[_],E](implicit F: MonadError[F,E])
    extends MonixToCatsMonad[F]()(F.monad) with _root_.cats.MonadError[F,E] {

    override def raiseError[A](e: E): F[A] =
      F.raiseError(e)
    override def handleErrorWith[A](fa: F[A])(f: (E) => F[A]): F[A] =
      F.onErrorHandleWith(fa)(f)
    override def handleError[A](fa: F[A])(f: (E) => A): F[A] =
      F.onErrorHandle(fa)(f)
    override def recover[A](fa: F[A])(pf: PartialFunction[E, A]): F[A] =
      F.onErrorRecover(fa)(pf)
    override def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
      F.onErrorRecoverWith(fa)(pf)
  }
}

private[cats] trait MonixToCatsCore4 extends MonixToCatsCore3 {
  /** Converts Monix's [[monix.types.Cobind Cobind]] instances into
    * the Cats `CoflatMap`.
    *
    * You can import [[monixToCatsCoflatMap]] in scope, or initiate/extend
    * the [[MonixToCatsCoflatMap]] class.
    */
  implicit def monixToCatsCoflatMap[F[_] : Cobind]: _root_.cats.CoflatMap[F] =
    new MonixToCatsCoflatMap[F]()

  /** Converts Monix's [[monix.types.Cobind Cobind]] instances into
    * the Cats `CoflatMap`.
    *
    * You can import [[monixToCatsCoflatMap]] in scope, or initiate/extend
    * the [[MonixToCatsCoflatMap]] class.
    */
  class MonixToCatsCoflatMap[F[_]](implicit F: Cobind[F])
    extends MonixToCatsFunctor[F]()(F.functor)
      with _root_.cats.CoflatMap[F] {

    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      F.coflatMap(fa)(f)
    override def coflatten[A](fa: F[A]): F[F[A]] =
      F.coflatten(fa)
  }
}


private[cats] trait MonixToCatsCore5 extends MonixToCatsCore4 {
  /** Converts Monix's [[monix.types.Comonad Comonad]] instances into
    * the Cats `Comonad`.
    *
    * You can import [[monixToCatsComonad]] in scope, or initiate/extend
    * the [[MonixToCatsComonad]] class.
    */
  implicit def monixToCatsComonad[F[_] : Comonad]: _root_.cats.Comonad[F] =
    new MonixToCatsComonad[F]()

  /** Converts Monix's [[monix.types.Comonad Comonad]] instances into
    * the Cats `Comonad`.
    *
    * You can import [[monixToCatsComonad]] in scope, or initiate/extend
    * the [[MonixToCatsComonad]] class.
    */
  class MonixToCatsComonad[F[_]](implicit F: Comonad[F])
    extends MonixToCatsCoflatMap[F]()(F.cobind)
      with _root_.cats.Comonad[F] {

    override def extract[A](x: F[A]): A =
      F.extract(x)
  }
}

private[cats] trait MonixToCatsCore6 extends MonixToCatsCore5 {
  /** Converts Monix's [[monix.types.Monad Monad]] and
    * [[monix.types.Comonad Comonad]] instances into
    * the Cats `Bimonad`.
    *
    * You can import [[monixToCatsBimonad]] in scope, or initiate/extend
    * the [[MonixToCatsBimonad]] class.
    */
  implicit def monixToCatsBimonad[F[_] : Monad : Comonad]: _root_.cats.Bimonad[F] =
    new MonixToCatsBimonad[F]()

  /** Converts Monix's [[monix.types.Monad Monad]] and
    * [[monix.types.Comonad Comonad]] instances into
    * the Cats `Bimonad`.
    *
    * You can import [[monixToCatsBimonad]] in scope, or initiate/extend
    * the [[MonixToCatsBimonad]] class.
    */
  class MonixToCatsBimonad[F[_]](implicit ev1: Monad[F], ev2: Comonad[F])
    extends MonixToCatsMonad[F]()(ev1) with _root_.cats.Bimonad[F] {

    override def extract[A](x: F[A]): A =
      ev2.extract(x)
    override def coflatMap[A, B](fa: F[A])(f: (F[A]) => B): F[B] =
      ev2.cobind.coflatMap(fa)(f)
  }
}

private[cats] trait MonixToCatsCore7 extends MonixToCatsCore6 {
  /** Converts Monix's [[monix.types.MonadFilter MonadFilter]] instances into
    * the Cats `MonadFilter`.
    *
    * You can import [[monixToCatsMonadFilter]] in scope, or initiate/extend
    * the [[MonixToCatsMonadFilter]] class.
    */
  implicit def monixToCatsMonadFilter[F[_] : MonadFilter]: _root_.cats.MonadFilter[F] =
    new MonixToCatsMonadFilter[F]()

  /** Converts Monix's [[monix.types.MonadFilter MonadFilter]] instances into
    * the Cats `MonadFilter`.
    *
    * You can import [[monixToCatsMonadFilter]] in scope, or initiate/extend
    * the [[MonixToCatsMonadFilter]] class.
    */
  class MonixToCatsMonadFilter[F[_]](implicit F: MonadFilter[F])
    extends MonixToCatsMonad[F]()(F.monad) with _root_.cats.MonadFilter[F] {

    override def empty[A]: F[A] =
      F.empty[A]
    override def filter[A](fa: F[A])(f: (A) => Boolean): F[A] =
      F.filter(fa)(f)
  }
}

private[cats] trait MonixToCatsCore8 extends MonixToCatsCore7 {
  /** Converts Monix's [[monix.types.SemigroupK SemigroupK]] instances into
    * the Cats `SemigroupK`.
    *
    * You can import [[monixToCatsSemigroupK]] in scope, or initiate/extend
    * the [[MonixToCatsSemigroupK]] class.
    */
  implicit def monixToCatsSemigroupK[F[_] : SemigroupK]: _root_.cats.SemigroupK[F] =
    new MonixToCatsSemigroupK[F]()

  /** Converts Monix's [[monix.types.SemigroupK SemigroupK]] instances into
    * the Cats `SemigroupK`.
    *
    * You can import [[monixToCatsSemigroupK]] in scope, or initiate/extend
    * the [[MonixToCatsSemigroupK]] class.
    */
  class MonixToCatsSemigroupK[F[_]](implicit F: SemigroupK[F])
    extends _root_.cats.SemigroupK[F] {

    override def combineK[A](x: F[A], y: F[A]): F[A] =
      F.combineK(x,y)
  }
}

private[cats] trait MonixToCatsCore9 extends MonixToCatsCore8 {
  /** Converts Monix's [[monix.types.MonoidK MonoidK]] instances into
    * the Cats `MonoidK`.
    *
    * You can import [[monixToCatsMonoidK]] in scope, or initiate/extend
    * the [[MonixToCatsMonoidK]] class.
    */
  implicit def monixToCatsMonoidK[F[_] : MonoidK]: _root_.cats.MonoidK[F] =
    new MonixToCatsMonoidK[F]()

  /** Converts Monix's [[monix.types.MonoidK MonoidK]] instances into
    * the Cats `MonoidK`.
    *
    * You can import [[monixToCatsMonoidK]] in scope, or initiate/extend
    * the [[MonixToCatsMonoidK]] class.
    */
  class MonixToCatsMonoidK[F[_]](implicit F: MonoidK[F])
    extends MonixToCatsSemigroupK[F]()(F.semigroupK)
      with _root_.cats.MonoidK[F] {

    override def empty[A]: F[A] = F.empty[A]
  }
}

private[cats] trait MonixToCatsCore10 extends  MonixToCatsCore9 {
  /** Converts Monix's [[monix.types.MonoidK MonoidK]] and Monix's
    * [[monix.types.MonadFilter MonadFilter]] instances into
    * the Cats `MonadCombine`.
    *
    * You can import [[monixToCatsMonadCombine]] in scope, or initiate/extend
    * the [[MonixToCatsMonadCombine]] class.
    */
  implicit def monixToCatsMonadCombine[F[_] : MonadFilter : MonoidK]: _root_.cats.MonadCombine[F] =
    new MonixToCatsMonadCombine[F]()

  /** Converts Monix's [[monix.types.MonoidK MonoidK]] and Monix's
    * [[monix.types.MonadFilter MonadFilter]] instances into
    * the Cats `MonadCombine`.
    *
    * You can import [[monixToCatsMonadCombine]] in scope, or initiate/extend
    * the [[MonixToCatsMonadCombine]] class.
    */
  class MonixToCatsMonadCombine[F[_]](implicit M: MonadFilter[F], MK: MonoidK[F])
    extends MonixToCatsMonadFilter[F]
      with _root_.cats.MonadCombine[F] {

    override def combineK[A](x: F[A], y: F[A]): F[A] =
      MK.semigroupK.combineK(x,y)
  }
}

private[cats] trait MonixToCatsCore11 extends MonixToCatsCore10 {
  /** Converts Monix's [[monix.types.MonadRec MonadRec]] instances into
    * the Cats `Monad`.
    *
    * You can import [[monixToCatsMonadRec]] in scope, or initiate/extend
    * the [[MonixToCatsMonadRec]] class.
    */
  implicit def monixToCatsMonadRec[F[_] : MonadRec]: _root_.cats.Monad[F] =
      new MonixToCatsMonadRec[F]()

  /** Converts Monix's [[monix.types.MonadRec MonadRec]] instances into
    * the Cats `Monad`.
    *
    * You can import [[monixToCatsMonadRec]] in scope, or initiate/extend
    * the [[MonixToCatsMonadRec]] class.
    */
  class MonixToCatsMonadRec[F[_]](implicit F: MonadRec[F])
    extends MonixToCatsMonad[F]()(F.monad) {

    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] =
      F.tailRecM(a)(f)
  }
}
