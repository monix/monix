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
import cats.data.{Xor, XorT}
import cats.laws.discipline.CartesianTests.Isomorphisms
import monix.laws.StreamableLaws
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import scala.language.higherKinds

trait StreamableTests[F[_]]
  extends MonadConsTests[F]
    with RecoverableTests[F, Throwable] {

  def laws: StreamableLaws[F]

  def streamable[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbEXorA: Arbitrary[Throwable Xor A],
    ArbFEXorA: Arbitrary[F[Throwable Xor A]],
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
      val name = "streamable"
      val parents = Seq(monadCons[A,B,C], recoverable[A,B,C])
      val props = Seq(
        "streamable endWith consistent with followWith" -> forAll(laws.streamableEndWithConsistentWithFollowWith[A] _),
        "streamable startWith consistent with followWith" -> forAll(laws.streamableStartWithConsistentWithFollowWith[A] _),
        "streamable repeat is consistent with fromList" -> forAll(laws.streamableRepeatIsConsistentWithFromList[A] _)
      )
    }
  }
}
