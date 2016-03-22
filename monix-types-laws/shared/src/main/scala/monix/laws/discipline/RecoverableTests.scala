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

package monix.laws.discipline

import cats.Eq
import cats.data.{XorT, Xor}
import cats.laws.discipline.CartesianTests.Isomorphisms
import cats.laws.discipline.MonadErrorTests
import monix.laws.RecoverableLaws
import monix.types.Recoverable
import org.scalacheck.Arbitrary
import scala.language.higherKinds

trait RecoverableTests[F[_],E] extends MonadErrorTests[F,E] {
  def laws: RecoverableLaws[F,E]

  def recoverable[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbE: Arbitrary[E],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqE: Eq[E],
    EqFXorEU: Eq[F[E Xor Unit]],
    EqFXorEA: Eq[F[E Xor A]],
    EqXorTFEA: Eq[XorT[F, E, A]],
    EqFABC: Eq[F[(A, B, C)]],
    iso: Isomorphisms[F]
  ): RuleSet = {
    new DefaultRuleSet(
      name = "recoverable",
      parent = Some(monadError[A,B,C])
    )
  }
}

object RecoverableTests {
  def apply[F[_],E](implicit F: Recoverable[F,E]): RecoverableTests[F,E] =
    new RecoverableTests[F,E] { def laws: RecoverableLaws[F,E] = RecoverableLaws[F,E] }
}