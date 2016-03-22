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
import monix.laws.AsyncLaws
import monix.types.Async
import org.scalacheck.Arbitrary
import scala.language.higherKinds

trait AsyncTests[F[_]] extends EvaluableTests[F] with RecoverableTests[F,Throwable] {
  def laws: AsyncLaws[F]

  def async[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbE: Arbitrary[Throwable],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqE: Eq[Throwable],
    EqFXorEU: Eq[F[Throwable Xor Unit]],
    EqFXorEA: Eq[F[Throwable Xor A]],
    EqXorTFEA: Eq[XorT[F, Throwable, A]],
    EqFABC: Eq[F[(A, B, C)]],
    iso: Isomorphisms[F]
  ): RuleSet = {
    new RuleSet {
      val bases = Nil
      val name = "async"
      val parents = Seq(evaluable[A,B,C], recoverable[A,B,C])
      def props = Seq.empty
    }
  }
}

object AsyncTests {
  def apply[F[_]](implicit F: Async[F]): AsyncTests[F] =
    new AsyncTests[F] { def laws: AsyncLaws[F] = AsyncLaws[F] }
}