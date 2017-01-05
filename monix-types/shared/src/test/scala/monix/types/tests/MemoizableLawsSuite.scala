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

import monix.types._
import org.scalacheck.Arbitrary

trait MemoizableLawsSuite[F[_],A,B,C] extends MonadLawsSuite[F,A,B,C] {
  override def F: Memoizable.Instance[F]

  object memoizableLaws extends Memoizable.Laws[F] {
    implicit def memoizable: Memoizable[F] = F
    implicit def monad: Monad[F] = F
    implicit def applicative: Applicative[F] = F
    implicit def functor: Functor[F] = F
  }

  def memoizableCheck(typeName: String, includeSupertypes: Boolean)(implicit
    arbitraryA: Arbitrary[A],
    arbitraryAtoA: Arbitrary[A => A],
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryABtoC: Arbitrary[(A,B) => C],
    arbitraryAtoFB: Arbitrary[A => F[B]],
    arbitraryBtoC: Arbitrary[B => C],
    arbitraryBtoFC: Arbitrary[B => F[C]],
    arbitraryFA: Arbitrary[F[A]],
    arbitraryFB: Arbitrary[F[B]],
    arbitraryFAtoB: Arbitrary[F[A => B]],
    arbitraryFBtoC: Arbitrary[F[B => C]],
    eqA: Eq[A],
    eqFA: Eq[F[A]],
    eqFB: Eq[F[B]],
    eqFC: Eq[F[C]]): Unit = {

    if (includeSupertypes) monadCheck(typeName, includeSupertypes)

    test(s"Memoizable[$typeName].evalOnceIsIdempotent") {
      check2((a: A, f: A => A) =>
        memoizableLaws.evalOnceIsIdempotent(a, f))
    }

    test(s"Memoizable[$typeName].memoizeIsIdempotent") {
      check2((a: A, f: A => A) =>
        memoizableLaws.memoizeIsIdempotent(a, f))
    }

    test(s"Memoizable[$typeName].evalOnceEquivalenceWithEval") {
      check2((a: A, f: A => A) =>
        memoizableLaws.evalOnceEquivalenceWithEval(a, f))
    }
  }
}
