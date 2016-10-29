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

import monix.types.{Applicative, Functor}
import org.scalacheck.Arbitrary

trait ApplicativeLawsSuite[F[_], A,B,C] extends FunctorLawsSuite[F,A,B,C] {
  override def F: Applicative.Instance[F]

  object applicativeLaws extends Applicative.Laws[F] {
    implicit def applicative: Applicative[F] = F
    implicit def functor: Functor[F] = F
  }

  def applicativeCheck(typeName: String, includeSupertypes: Boolean)(implicit
    arbitraryA: Arbitrary[A],
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryBtoC: Arbitrary[B => C],
    arbitraryFA: Arbitrary[F[A]],
    arbitraryFAtoB: Arbitrary[F[A => B]],
    arbitraryFBtoC: Arbitrary[F[B => C]],
    eqFA: Eq[F[A]],
    eqFB: Eq[F[B]],
    eqFC: Eq[F[C]]): Unit = {

    if (includeSupertypes) functorCheck(typeName)

    test(s"Applicative[$typeName].applyComposition") {
      check3((fa: F[A], fab: F[A => B], fbc: F[B => C]) =>
        applicativeLaws.applyComposition(fa, fab, fbc))
    }

    test(s"Applicative[$typeName].applicativeIdentity") {
      check1((fa: F[A]) => applicativeLaws.applicativeIdentity(fa))
    }

    test(s"Applicative[$typeName].applicativeHomomorphism") {
      check2((a: A, f: A => B) => applicativeLaws.applicativeHomomorphism(a, f))
    }

    test(s"Applicative[$typeName].applicativeInterchange") {
      check2((a: A, ff: F[A => B]) => applicativeLaws.applicativeInterchange(a, ff))
    }

    test(s"Applicative[$typeName].applicativeMap") {
      check2((fa: F[A], f: A => B) => applicativeLaws.applicativeMap(fa, f))
    }

    test(s"Applicative[$typeName].applicativeComposition") {
      check3((fa: F[A], fab: F[A => B], fbc: F[B => C]) =>
        applicativeLaws.applicativeComposition(fa, fab, fbc))
    }
  }
}
