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

package monix.types.tests

import monix.types.{Applicative, Functor, Monad, MonadError}
import org.scalacheck.Arbitrary

trait MonadErrorLawsSuite[F[_],A,B,C,E] extends MonadLawsSuite[F,A,B,C] {
  override def F: MonadError.Instance[F,E]

  object monadErrorLaws extends MonadError.Laws[F,E] {
    implicit def monadError: MonadError[F, E] = F
    implicit def monad: Monad[F] = F
    implicit def applicative: Applicative[F] = F
    implicit def functor: Functor[F] = F
  }

  def monadErrorCheck(typeName: String, includeSupertypes: Boolean)(implicit
    arbitraryA: Arbitrary[A],
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryABtoC: Arbitrary[(A,B) => C],
    arbitraryAtoFB: Arbitrary[A => F[B]],
    arbitraryBtoC: Arbitrary[B => C],
    arbitraryBtoFC: Arbitrary[B => F[C]],
    arbitraryE: Arbitrary[E],
    arbitraryEtoA: Arbitrary[E => A],
    arbitraryEtoAPF: Arbitrary[PartialFunction[E, A]],
    arbitraryEtoB: Arbitrary[E => B],
    arbitraryEtoFA: Arbitrary[E => F[A]],
    arbitraryEtoFB: Arbitrary[E => F[B]],
    arbitraryFA: Arbitrary[F[A]],
    arbitraryFB: Arbitrary[F[B]],
    arbitraryFAtoB: Arbitrary[F[A => B]],
    arbitraryFBtoC: Arbitrary[F[B => C]],
    eqFA: Eq[F[A]],
    eqFB: Eq[F[B]],
    eqFC: Eq[F[C]],
    eqFEitherEA: Eq[F[Either[E, A]]]): Unit = {

    if (includeSupertypes) monadCheck(typeName, includeSupertypes)

    test(s"MonadError[$typeName].monadErrorLeftZero") {
      check2((e: E, f: A => F[B]) =>
        monadErrorLaws.monadErrorLeftZero(e, f))
    }

    test(s"MonadError[$typeName].applicativeErrorHandleWith") {
      check2((e: E, f: E => F[B]) =>
        monadErrorLaws.applicativeErrorHandleWith(e, f))
    }

    test(s"MonadError[$typeName].applicativeErrorHandle") {
      check2((e: E, f: E => B) =>
        monadErrorLaws.applicativeErrorHandle(e, f))
    }

    test(s"MonadError[$typeName].onErrorHandleWithPure") {
      check2((a: A, fe: E => F[A]) =>
        monadErrorLaws.onErrorHandleWithPure[A](a, fe))
    }

    test(s"MonadError[$typeName].onErrorHandlePure") {
      check2((a: A, f: E => A) =>
        monadErrorLaws.onErrorHandlePure(a, f))
    }

    test(s"MonadError[$typeName].onErrorHandleWithConsistentWithRecoverWith") {
      check2((fa: F[A], f: E => F[A]) =>
        monadErrorLaws.onErrorHandleWithConsistentWithRecoverWith(fa, f))
    }

    test(s"MonadError[$typeName].onErrorHandleConsistentWithRecover") {
      check2((fa: F[A], f: E => A) =>
        monadErrorLaws.onErrorHandleConsistentWithRecover(fa, f))
    }

    test(s"MonadError[$typeName].recoverConsistentWithRecoverWith") {
      check2((fa: F[A], pf: PartialFunction[E, A]) =>
        monadErrorLaws.recoverConsistentWithRecoverWith(fa, pf))
    }

    test(s"MonadError[$typeName].raiseErrorAttempt") {
      check1 { (e: E) => monadErrorLaws.raiseErrorAttempt[A](e) }
    }

    test(s"MonadError[$typeName].pureAttempt") {
      check1 { (a: A) => monadErrorLaws.pureAttempt(a) }
    }
  }
}
