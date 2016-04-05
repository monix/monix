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

package monix.cats.tests

import cats._
import cats.data.{Xor, XorT}
import cats.laws.discipline.CartesianTests.Isomorphisms
import cats.laws.discipline.{CoflatMapTests, MonadErrorTests}
import cats.laws.{CoflatMapLaws, MonadErrorLaws}
import monix.cats.Deferrable
import org.scalacheck.Arbitrary

import scala.language.higherKinds

trait DeferrableTests[F[_]] extends MonadErrorTests[F, Throwable] with CoflatMapTests[F]  {
  def laws: MonadErrorLaws[F, Throwable] with CoflatMapLaws[F]

  def deferrable[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
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
      val name = "deferrable"
      val bases = Nil
      val parents = Seq(monadError[A,B,C], coflatMap[A,B,C])
      val props = Seq.empty
    }
  }
}

object DeferrableTests {
  type Laws[F[_]] = MonadErrorLaws[F, Throwable] with CoflatMapLaws[F]

  def apply[F[_] : Deferrable]: DeferrableTests[F] = {
    val ev = implicitly[Deferrable[F]]

    new DeferrableTests[F] {
      def laws: Laws[F] =
        new MonadErrorLaws[F, Throwable] with CoflatMapLaws[F] {
          implicit override def F: Deferrable[F] = ev
        }
    }
  }
}