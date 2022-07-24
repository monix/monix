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

class CoevalOptionSuite extends BaseTestSuite {
  test("Coeval.none should return a Now with a None") {
    val c = Coeval.none[Int]

    assertEquals(c.value(), None)
  }

  test("Coeval.some should return a Now with a Some") {
    val c = Coeval.some[Int](1)

    assertEquals(c.value(), Some(1))
  }
}
