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

package monix.laws
package discipline

import cats.laws.discipline.FunctorTests
import cats.{Eq, Monoid}
import monix.types._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import scala.language.higherKinds

trait FoldableFTests[F[_]] extends FunctorTests[F] {
  def laws: FoldableFLaws[F]

  def foldableF[A: Arbitrary, B: Arbitrary, C: Arbitrary](implicit
    ArbFA: Arbitrary[F[A]],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqB: Eq[B],
    EqFBool: Eq[F[Boolean]],
    B: Monoid[B]
  ): RuleSet = {
    new DefaultRuleSet(
      name = "foldableF",
      parent = Some(functor[A,B,C]),
      "leftFoldF consistent with foldMapF" -> forAll(laws.leftFoldConsistentWithFoldMapF[A,B] _),
      "existsF consistent with findF" -> forAll(laws.existsConsistentWithFind[A] _)
    )
  }
}


object FoldableFTests {
  def apply[F[_]: FoldableF]: FoldableFTests[F] =
    new FoldableFTests[F] { def laws: FoldableFLaws[F] = FoldableFLaws[F] }
}
