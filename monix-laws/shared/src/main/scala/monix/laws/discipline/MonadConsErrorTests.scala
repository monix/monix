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

import cats.{Monoid, Eq}
import cats.data.{Xor, XorT}
import cats.laws.discipline.CartesianTests.Isomorphisms
import monix.laws.MonadConsErrorLaws
import monix.types.MonadConsError
import org.scalacheck.Arbitrary
import scala.language.higherKinds

/** Tests specification for the [[monix.types.MonadConsError MonadConsError]] type-class,
  * powered by [[https://github.com/typelevel/discipline/ Discipline]].
  *
  * See [[MonadConsErrorLaws]] for the defined laws.
  */
trait MonadConsErrorTests[F[_],E] extends MonadConsTests[F] with RecoverableTests[F,E] {
  def laws: MonadConsErrorLaws[F,E]

  def monadConsError[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbEXorA: Arbitrary[E Xor A],
    ArbFEXorA: Arbitrary[F[E Xor A]],
    ArbE: Arbitrary[E],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqE: Eq[E],
    EqFXorEU: Eq[F[E Xor Unit]],
    EqFXorEA: Eq[F[E Xor A]],
    EqXorTFEA: Eq[XorT[F, E, A]],
    EqFABC: Eq[F[(A, B, C)]],
    EqFBool: Eq[F[Boolean]],
    B: Monoid[B],
    iso: Isomorphisms[F]
  ): RuleSet = {
    new RuleSet {
      val bases = Nil
      val name = "monadConsError"
      val parents = Seq(monadCons[A,B,C], recoverable[A,B,C])
      val props = Seq.empty
    }
  }
}

object MonadConsErrorTests {
  def apply[F[_],E](implicit ev: MonadConsError[F,E]): MonadConsErrorTests[F,E] =
    new MonadConsErrorTests[F,E] { def laws: MonadConsErrorLaws[F,E] = MonadConsErrorLaws[F,E] }
}