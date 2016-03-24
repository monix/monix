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

import cats.Eq
import cats.laws.discipline.BimonadTests
import cats.laws.discipline.CartesianTests.Isomorphisms
import monix.types.Evaluable
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import scala.language.higherKinds

/** Tests specification for the [[monix.types.Evaluable Evaluable]] type-class,
  * powered by [[https://github.com/typelevel/discipline/ Discipline]].
  *
  * See [[EvaluableLaws]] for the defined laws.
  */
trait EvaluableTests[F[_]] extends NonstrictTests[F] with BimonadTests[F] {
  def laws: EvaluableLaws[F]

  def evaluable[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbFFA: Arbitrary[F[F[A]]],
    EqFFFA: Eq[F[F[A]]],
    EqFFA: Eq[F[F[F[A]]]],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFABC: Eq[F[(A, B, C)]],
    iso: Isomorphisms[F]
  ): RuleSet = {
    new RuleSet {
      val bases = Nil
      val name = "evaluable"
      val parents = Seq(nonstrict[A,B,C], bimonad[A,B,C])
      val props = Seq(
        "now can nonstrict" -> forAll(laws.lazyNowCanEvaluate[A] _),
        "evalAlways can evaluate" -> forAll(laws.lazyEvalAlwaysCanEvaluate[A] _),
        "evalOnce can evaluate" -> forAll(laws.lazyEvalOnceCanEvaluate[A] _),
        "for now extract is alias of value" -> forAll(laws.lazyForNowExtractIsAliasOfValue[A] _),
        "for always extract is alias of value" -> forAll(laws.lazyForAlwaysExtractIsAliasOfValue[A] _),
        "for once extract is alias of value" -> forAll(laws.lazyForOnceExtractIsAliasOfValue[A] _)
      )
    }
  }
}

object EvaluableTests {
  def apply[F[_]](implicit F: Evaluable[F]): EvaluableTests[F] =
    new EvaluableTests[F] { def laws: EvaluableLaws[F] = EvaluableLaws[F] }
}