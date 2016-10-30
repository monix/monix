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

import monix.types.{Cobind, Functor}
import org.scalacheck.Arbitrary

trait CobindLawsSuite[F[_],A,B,C] extends FunctorLawsSuite[F,A,B,C] {
  override def F: Cobind.Instance[F]

  object cobindLaws extends Cobind.Laws[F] {
    implicit def functor: Functor[F] = F
    implicit def cobind: Cobind[F] = F
  }

  def cobindCheck(typeName: String, includeSupertypes: Boolean)(implicit
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryBtoC: Arbitrary[B => C],
    arbitraryFA: Arbitrary[F[A]],
    arbitraryFAtoB: Arbitrary[F[A] => B],
    arbitraryFBtoC: Arbitrary[F[B] => C],
    eqFA: Eq[F[A]],
    eqFB: Eq[F[B]],
    eqFC: Eq[F[C]],
    eqFFA: Eq[F[F[A]]],
    eqFFFA: Eq[F[F[F[A]]]]): Unit = {

    if (includeSupertypes) functorCheck(typeName)

    test(s"Cobind[$typeName].coflatMapAssociativity") {
      check3((fa: F[A], fab: F[A] => B, fbc: F[B] => C) =>
        cobindLaws.coflatMapAssociativity(fa, fab, fbc))
    }

    test(s"Cobind[$typeName].coflattenThroughMap") {
      check1((fa: F[A]) => cobindLaws.coflattenThroughMap(fa))
    }

    test(s"Cobind[$typeName].coflattenCoherence") {
      check2((fa: F[A], fab: F[A] => B) => cobindLaws.coflattenCoherence(fa,fab))
    }

    test(s"Cobind[$typeName].coflatMapIdentity") {
      check1((fa: F[A]) => cobindLaws.coflatMapIdentity(fa))
    }
  }
}
