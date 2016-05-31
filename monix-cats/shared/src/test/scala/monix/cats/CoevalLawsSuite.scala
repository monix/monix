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

package monix.cats

import cats.Eq
import cats.kernel.laws.GroupLaws
import cats.laws.discipline.{ComonadTests, MonadErrorTests}
import monix.eval.Coeval
import org.scalacheck.Arbitrary

object CoevalLawsSuite extends BaseLawsSuite with GroupLaws[Coeval[Int]] {
  // for GroupLaws
  override def Equ: Eq[Coeval[Int]] = equalityCoeval[Int]
  override def Arb: Arbitrary[Coeval[Int]] = arbitraryCoeval[Int]

  checkAll("Group[Coeval[Int]]", GroupLaws[Coeval[Int]].group)
  checkAll("Monoid[Coeval[Int]]", GroupLaws[Coeval[Int]].monoid)
  checkAll("Semigroup[Coeval[Int]]", GroupLaws[Coeval[Int]].semigroup)

  checkAll("MonadError[Coeval[Int]]", MonadErrorTests[Coeval, Throwable].monadError[Int,Int,Int])
  checkAll("Comonad[Coeval[Int]]", ComonadTests[Coeval].comonad[Int,Int,Int])
}
