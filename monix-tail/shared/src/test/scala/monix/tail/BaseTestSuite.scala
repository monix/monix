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

package monix.tail

import monix.execution.internal.Platform
import org.scalacheck.{ Arbitrary, Prop }
import org.scalacheck.Test.Parameters

/** Just a marker for what we need to extend in the tests
  * of `monix-tail`.
  */
trait BaseTestSuite extends monix.eval.BaseTestSuite with ArbitraryInstances {
  override def scalaCheckTestParameters: Parameters =
    super.scalaCheckTestParameters
      .withMinSuccessfulTests(if (Platform.isJVM) 200 else 20)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)
      .withMaxSize(24)

  def check4[A: Arbitrary, B: Arbitrary, C: Arbitrary, D: Arbitrary](f: (A, B, C, D) => Prop): Unit =
    check(Prop.forAll(f))

  def check5[A: Arbitrary, B: Arbitrary, C: Arbitrary, D: Arbitrary, E: Arbitrary](
    f: (A, B, C, D, E) => Prop
  ): Unit =
    check(Prop.forAll(f))
}
