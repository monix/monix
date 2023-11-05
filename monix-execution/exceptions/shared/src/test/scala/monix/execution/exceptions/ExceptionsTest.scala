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

package monix.execution.exceptions

import minitest.SimpleTestSuite

object ExceptionsTest extends SimpleTestSuite {
  test("platform capabilities — suppressed exceptions") {
    val s0 = new RuntimeException("s0")
    val s1 = new RuntimeException("s1")
    val s2 = new RuntimeException("s2")

    s0.addSuppressed(s1)
    s0.addSuppressed(s2)

    assertEquals(
      s0.getSuppressed().toList,
      List(s1, s2)
    )
  }
}
