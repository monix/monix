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

import cats.laws.discipline.CartesianTests.Isomorphisms
import cats.laws.discipline.MonadFilterTests
import cats.{Eq, Monoid}
import monix.laws.MonadConsLaws
import monix.types._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import scala.language.higherKinds

/** Tests specification for the [[monix.types.MonadCons MonadCons]] type-class,
  * powered by [[https://github.com/typelevel/discipline/ Discipline]].
  *
  * See [[MonadConsLaws]] for the defined laws.
  */
trait MonadConsTests[F[_]] extends MonadFilterTests[F] with FoldableFTests[F] {
  def laws: MonadConsLaws[F]

  def monadCons[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFABC: Eq[F[(A, B, C)]],
    EqFBool: Eq[F[Boolean]],
    B: Monoid[B],
    iso: Isomorphisms[F]
  ): RuleSet = {
    new RuleSet {
      val bases = Nil
      val name = "monadCons"
      val parents = Seq(foldableF[A,B,C], monadFilter[A,B,C])
      val props = Seq(
        "monadCons flatMap is aliased" -> forAll(laws.monadConsFlatMapIsAliased[A,B] _),
        "monadCons flatten is aliased" -> forAll(laws.monadConsFlattenIsAliased[A,B] _),
        "monadCons collect is consistent with filter" -> forAll(laws.monadConsCollectIsConsistentWithFilter[A,B] _),
        "monadCons followWith is consistent with cons" -> forAll(laws.monadConsFollowWithIsConsistentWithCons[A] _),
        "monadCons followWith is transitive" -> forAll(laws.monadConsFollowWithIsTransitive[A] _),
        "monadCons followWith empty mirrors source" -> forAll(laws.monadConsFollowWithEmptyMirrorsSource[A] _),
        "monadCons empty followWith mirrors source" -> forAll(laws.monadConsEmptyFollowWithMirrorsSource[A] _),
        "monadCons endWithElem consistent with followWith" -> forAll(laws.monadConsEndWithElemConsistentWithFollowWith[A] _),
        "monadCons startWithElem consistent with followWith" -> forAll(laws.monadConsStartWithElemConsistentWithFollowWith[A] _)
      )
    }
  }
}

object MonadConsTests {
  def apply[F[_]](implicit F: MonadCons[F]): MonadConsTests[F] =
    new MonadConsTests[F] { def laws: MonadConsLaws[F] = MonadConsLaws[F] }
}