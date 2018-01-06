/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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
import org.scalacheck.Test.Parameters

/** Just a marker for what we need to extend in the tests
  * of `monix-tail`.
  */
trait BaseTestSuite extends monix.eval.BaseTestSuite with ArbitraryInstances {
  override lazy val checkConfig: Parameters =
    Parameters.default
      .withMinSuccessfulTests(if (Platform.isJVM) 200 else 20)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)
      .withMaxSize(24)
}