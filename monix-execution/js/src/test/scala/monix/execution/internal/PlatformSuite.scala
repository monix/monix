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

package monix.execution.internal

import minitest.SimpleTestSuite

object PlatformSuite extends SimpleTestSuite {
  test("isJS") {
    assert(!Platform.isJVM, "!isJVM")
    assert(Platform.isJS, "isJS")
  }

  test("recommendedBatchSize default ") {
    assertEquals(Platform.recommendedBatchSize, 512)
  }

  test("autoCancelableRunLoops") {
    assert(Platform.autoCancelableRunLoops)
  }

  test("localContextPropagation") {
    assert(!Platform.localContextPropagation)
  }
}
