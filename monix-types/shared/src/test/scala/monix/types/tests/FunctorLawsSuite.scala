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

import monix.types.Functor
import org.scalacheck.Arbitrary

trait FunctorLawsSuite[F[_],A,B,C] extends BaseLawsSuite { self =>
  def F: Functor.Instance[F]

  object functorLaws extends Functor.Laws[F] {
    implicit def functor: Functor[F] = F
  }

  def functorCheck(typeName: String)(implicit
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryBtoC: Arbitrary[B => C],
    arbitraryFA: Arbitrary[F[A]],
    eqFA: Eq[F[A]],
    eqFC: Eq[F[C]]): Unit = {

    test(s"Functor[$typeName].covariantIdentity") {
      check1((fa: F[A]) => functorLaws.covariantIdentity(fa))
    }

    test(s"Functor[$typeName].covariantComposition") {
      check3((fa: F[A], f: A => B, g: B => C) =>
        functorLaws.covariantComposition(fa, f, g))
    }
  }
}
