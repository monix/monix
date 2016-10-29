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

import monix.types.{Applicative, Functor, Monad, MonadFilter}
import org.scalacheck.Arbitrary

trait MonadFilterLawsSuite[F[_],A,B,C] extends MonadLawsSuite[F,A,B,C] {
  override def F: MonadFilter.Instance[F]

  object monadFilterLaws extends MonadFilter.Laws[F] {
    implicit def monadFilter: MonadFilter[F] = F
    implicit def monad: Monad[F] = F
    implicit def applicative: Applicative[F] = F
    implicit def functor: Functor[F] = F
  }

  def monadFilterCheck(typeName: String, includeSupertypes: Boolean)(implicit
    arbitraryA: Arbitrary[A],
    arbitraryAtoB: Arbitrary[A => B],
    arbitraryAtoBool: Arbitrary[A => Boolean],
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

    if (includeSupertypes) monadCheck(typeName, includeSupertypes)

    test(s"MonadFilter[$typeName].monadFilterLeftEmpty") {
      check1((f: A => F[B]) =>
        monadFilterLaws.monadFilterLeftEmpty(f))
    }

    test(s"MonadFilter[$typeName].monadFilterRightEmpty") {
      check1((fa: F[A]) => monadFilterLaws.monadFilterRightEmpty[A,B](fa))
    }

    test(s"MonadFilter[$typeName].monadFilterConsistency") {
      check2((fa: F[A], f: A => Boolean) =>
        monadFilterLaws.monadFilterConsistency(fa, f))
    }
  }
}
