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
import monix.types.Nonstrict
import monix.laws.NonstrictLaws
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import scala.language.higherKinds

trait NonstrictTests[F[_]] extends EvaluableTests[F] {
  def laws: NonstrictLaws[F]

  def nonstrict[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
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
      name = "nonstrict",
      parent = Some(evaluable[A,B,C]),
      "now can evaluable" -> forAll(laws.nowCanEvaluate[A] _),
      "evalAlways can evaluate" -> forAll(laws.evalAlwaysCanEvaluate[A] _),
      "evalOnce can evaluate" -> forAll(laws.evalOnceCanEvaluate[A] _)
    )
  }
}

object NonstrictTests {
  def apply[F[_]](implicit F: Nonstrict[F]): NonstrictTests[F] =
    new NonstrictTests[F] { def laws: NonstrictLaws[F] = NonstrictLaws[F] }
}