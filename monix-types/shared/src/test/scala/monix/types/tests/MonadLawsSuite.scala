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

import monix.types.{Applicative, Functor, Monad}
import org.scalacheck.Arbitrary

trait MonadLawsSuite[F[_], A,B,C] extends ApplicativeLawsSuite[F,A,B,C] {
  override def F: Monad.Instance[F]

  object monadLaws extends Monad.Laws[F] {
    implicit def monad: Monad[F] = F
    implicit def applicative: Applicative[F] = F
    implicit def functor: Functor[F] = F
  }

  def monadCheck(typeName: String, includeSupertypes: Boolean)(implicit
    arbitraryA: Arbitrary[A],
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryABtoC: Arbitrary[(A,B) => C],
    arbitraryAtoFB: Arbitrary[A => F[B]],
    arbitraryBtoC: Arbitrary[B => C],
    arbitraryBtoFC: Arbitrary[B => F[C]],
    arbitraryFA: Arbitrary[F[A]],
    arbitraryFB: Arbitrary[F[B]],
    arbitraryFAtoB: Arbitrary[F[A => B]],
    arbitraryFBtoC: Arbitrary[F[B => C]],
    eqFA: Eq[F[A]],
    eqFB: Eq[F[B]],
    eqFC: Eq[F[C]]): Unit = {

    if (includeSupertypes) applicativeCheck(typeName, includeSupertypes)

    test(s"Monad[$typeName].flatMapAssociativity") {
      check3((fa: F[A], fafb: A => F[B], fbfc: B => F[C]) =>
        monadLaws.flatMapAssociativity(fa, fafb, fbfc))
    }

    test(s"Monad[$typeName].flatMapConsistentApply") {
      check2((fa: F[A], fab: F[A => B]) =>
        monadLaws.flatMapConsistentApply(fa, fab))
    }

    test(s"Monad[$typeName].flatMapConsistentMap2") {
      check3((fa: F[A], fb: F[B], f: (A,B) => C) =>
        monadLaws.flatMapConsistentMap2(fa, fb, f))
    }

    test(s"Monad[$typeName].suspendEquivalenceWithEval") {
      check1((a: A) =>
        monadLaws.suspendEquivalenceWithEval(a))
    }

    test(s"Monad[$typeName].evalEquivalenceWithSuspend") {
      check1((fa: F[A]) =>
        monadLaws.evalEquivalenceWithSuspend(fa))
    }
  }
}
