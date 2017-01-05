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

package monix.eval

import minitest.TestSuite
import minitest.laws.Checkers
import monix.execution.schedulers.TestScheduler
import monix.types.tests.Eq
import org.scalacheck.Prop

trait BaseTestSuite extends TestSuite[TestScheduler]
  with Checkers with ArbitraryInstances {

  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")
  }

  implicit def isEquivToProp[A](p: IsEquiv[A])(implicit A: Eq[A]): Prop =
    Prop(A(p.lh, p.rh))

  implicit def isEquivListToProp[A](ns: List[IsEquiv[A]])(implicit A: Eq[A]): Prop =
    Prop(ns.forall(p => A(p.lh, p.rh)))

  implicit def isNotEquivToProp[A](p: IsNotEquiv[A])(implicit A: Eq[A]): Prop =
    Prop(!A(p.lh, p.rh))

  implicit def isNotEquivListToProp[A](ns: List[IsNotEquiv[A]])(implicit A: Eq[A]): Prop =
    Prop(ns.forall(p => !A(p.lh, p.rh)))
}

/** For generating dummy exceptions. */
case class DummyException(message: String) extends RuntimeException(message)

/** For expressing equivalence. */
final case class IsEquiv[A](lh: A, rh: A)

/** For negating equivalence. */
final case class IsNotEquiv[A](lh: A, rh: A)
