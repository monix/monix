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

import monix.types.{Cobind, Comonad, Functor}
import org.scalacheck.Arbitrary

trait ComonadLawsSuite[F[_],A,B,C] extends CobindLawsSuite[F,A,B,C] {
  override def F: Comonad.Instance[F]

  object comonadLaws extends Comonad.Laws[F] {
    implicit def functor: Functor[F] = F
    implicit def cobind: Cobind[F] = F
    implicit def comonad: Comonad[F] = F
  }

  def comonadCheck(typeName: String, includeSupertypes: Boolean)(implicit
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryBtoC: Arbitrary[B => C],
    arbitraryFA: Arbitrary[F[A]],
    arbitraryFAtoB: Arbitrary[F[A] => B],
    arbitraryFBtoC: Arbitrary[F[B] => C],
    eqB: Eq[B],
    eqFA: Eq[F[A]],
    eqFB: Eq[F[B]],
    eqFC: Eq[F[C]],
    eqFFA: Eq[F[F[A]]],
    eqFFFA: Eq[F[F[F[A]]]]): Unit = {

    if (includeSupertypes) cobindCheck(typeName, includeSupertypes)

    test(s"Comonad[$typeName].extractCoflattenIdentity") {
      check1((fa: F[A]) =>
        comonadLaws.extractCoflattenIdentity(fa))
    }

    test(s"Comonad[$typeName].mapCoflattenIdentity") {
      check1((fa: F[A]) =>
        comonadLaws.mapCoflattenIdentity(fa))
    }

    test(s"Comonad[$typeName].mapCoflatMapCoherence") {
      check2((fa: F[A], f: A => B) =>
        comonadLaws.mapCoflatMapCoherence(fa, f))
    }

    test(s"Comonad[$typeName].comonadLeftIdentity") {
      check1((fa: F[A]) =>
        comonadLaws.comonadLeftIdentity(fa))
    }

    test(s"Comonad[$typeName].comonadRightIdentity") {
      check2((fa: F[A], f: F[A] => B) =>
        comonadLaws.comonadRightIdentity(fa, f))
    }
  }
}
