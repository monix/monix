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

trait MonadRecLawsSuite[F[_],A,B,C] extends MonadLawsSuite[F,A,B,C] {
  override def F: MonadRec.Instance[F]

  object monadRecLaws extends MonadRec.Laws[F] {
    implicit def monadRec: MonadRec[F] = F
    implicit def monad: Monad[F] = F
    implicit def applicative: Applicative[F] = F
    implicit def functor: Functor[F] = F
  }

  def monadRecCheck(typeName: String, includeSupertypes: Boolean, stackSafetyCount: Int = 50000)(implicit
    arbitraryA: Arbitrary[A],
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryABtoC: Arbitrary[(A,B) => C],
    arbitraryAtoFA: Arbitrary[A => F[A]],
    arbitraryAtoFB: Arbitrary[A => F[B]],
    arbitraryBtoC: Arbitrary[B => C],
    arbitraryBtoFC: Arbitrary[B => F[C]],
    arbitraryFA: Arbitrary[F[A]],
    arbitraryFB: Arbitrary[F[B]],
    arbitraryFAtoB: Arbitrary[F[A => B]],
    arbitraryFBtoC: Arbitrary[F[B => C]],
    eqFA: Eq[F[A]],
    eqFB: Eq[F[B]],
    eqFC: Eq[F[C]],
    eqFInt: Eq[F[Int]]): Unit = {

    if (includeSupertypes) monadCheck(typeName, includeSupertypes)

    test(s"MonadRec[$typeName].tailRecMConsistentFlatMap") {
      check3((count: Int, a: A, f: A => F[A]) =>
        monadRecLaws.tailRecMConsistentFlatMap(count, a, f))
    }

    test(s"MonadRec[$typeName].tailRecMStackSafety($stackSafetyCount)") {
      val prop = monadRecLaws.tailRecMStackSafety(stackSafetyCount)
      assert(eqFInt(prop.lh, prop.rh))
    }
  }
}