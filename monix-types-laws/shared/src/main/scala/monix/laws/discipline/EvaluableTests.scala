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
import cats.laws.discipline.CartesianTests.Isomorphisms
import cats.laws.discipline.MonadTests
import monix.types.Evaluable
import monix.laws.EvaluableLaws
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import scala.language.higherKinds

trait EvaluableTests[F[_]] extends MonadTests[F] {
  def laws: EvaluableLaws[F]

  def evaluable[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFABC: Eq[F[(A, B, C)]],
    iso: Isomorphisms[F]
  ): RuleSet = {
    new DefaultRuleSet(
      name = "evaluable",
      parent = Some(monad[A,B,C]),
      "evaluable evalOnce equivalence" -> forAll(laws.evaluableOnceEquivalence[A] _),
      "evaluable evalAlways equivalence" -> forAll(laws.evaluableAlwaysEquivalence[A] _),
      "evaluable defer equivalence" -> forAll(laws.evaluableDeferEquivalence[A] _)
    )
  }
}

object EvaluableTests {
  def apply[F[_]](implicit F: Evaluable[F]): EvaluableTests[F] =
    new EvaluableTests[F] { def laws: EvaluableLaws[F] = EvaluableLaws[F] }
}