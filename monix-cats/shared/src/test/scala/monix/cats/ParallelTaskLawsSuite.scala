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
import cats.laws.discipline.ApplicativeTests
import monix.eval.Task
import monix.eval.Task.nondeterminism
import org.scalacheck.Arbitrary

object ParallelTaskLawsSuite extends BaseLawsSuite with GroupLaws[Task[Int]] {
  // for GroupLaws
  override def Equ: Eq[Task[Int]] = equalityTask[Int]
  override def Arb: Arbitrary[Task[Int]] = arbitraryTask[Int]

  checkAll("Group[ParallelTask[Int]]", GroupLaws[Task[Int]].group)
  checkAll("Monoid[ParallelTask[Int]]", GroupLaws[Task[Int]].monoid)
  checkAll("Semigroup[ParallelTask[Int]]", GroupLaws[Task[Int]].semigroup)
  checkAll("Applicative[ParallelTask[Int]]", ApplicativeTests[Task].applicative[Int,Int,Int])
}