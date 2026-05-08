/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import monix.execution.TestSchedulerSuite
import munit.Location
import monix.execution.internal.Platform
import org.scalacheck.{ Arbitrary, Prop, Test }

abstract class BaseTestSuite extends TestSchedulerSuite with ArbitraryInstances {
  // TestSchedulerSuite provides createTestScheduler() and assertNoRemainingTasks()
  // Use test("name") { implicit s => ... } in leaf suites

  override def scalaCheckTestParameters: Test.Parameters =
    super.scalaCheckTestParameters
      .withMinSuccessfulTests(if (Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)

  def check(prop: Prop): Unit = {
    val result = Test.check(scalaCheckTestParameters, prop)
    assert(result.passed, clue(result.toString))
  }

  def check1[A: Arbitrary](f: A => Prop): Unit =
    check(Prop.forAll(f))

  def check3[A: Arbitrary, B: Arbitrary, C: Arbitrary](f: (A, B, C) => Prop): Unit =
    check(Prop.forAll(f))

  def fail()(implicit loc: Location): Nothing =
    fail("failed")
}
