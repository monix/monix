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
import cats.laws.discipline.CartesianTests.Isomorphisms
import cats.laws.discipline.{CoflatMapTests, MonadTests}
import monix.types.Nonstrict
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import scala.language.higherKinds

/** Tests specification for the [[monix.types.Nonstrict Nonstrict]] type-class,
  * powered by [[https://github.com/typelevel/discipline/ Discipline]].
  *
  * See [[NonstrictLaws]] for the defined laws.
  */
trait NonstrictTests[F[_]] extends MonadTests[F] with CoflatMapTests[F] {
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
    new RuleSet {
      val bases = Nil
      val name = "nonstrict"
      val parents = Seq(monad[A,B,C], coflatMap[A,B,C])
      val props = Seq(
        "evalOnce equivalence" -> forAll(laws.evaluableOnceEquivalence[A] _),
        "evalAlways equivalence" -> forAll(laws.evaluableAlwaysEquivalence[A] _),
        "defer equivalence" -> forAll(laws.evaluableDeferEquivalence[A] _),
        "memoize equivalence" -> forAll(laws.evaluableMemoizeEquivalence[A] _)
      )
    }
  }
}

object NonstrictTests {
  def apply[F[_]](implicit F: Nonstrict[F]): NonstrictTests[F] =
    new NonstrictTests[F] { def laws: NonstrictLaws[F] = NonstrictLaws[F] }
}