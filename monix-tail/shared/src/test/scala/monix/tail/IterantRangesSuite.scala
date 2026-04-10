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

import monix.eval.Coeval

object IterantRangesSuite extends BaseTestSuite {
  test("Iterant.range(0, 10, 1)") { implicit s =>
    val lst = Iterant[Coeval].range(0, 10).toListL.value()
    assertEquals(lst, (0 until 10).toList)
  }

  test("Iterant.range(0, 10, 2)") { implicit s =>
    val lst = Iterant[Coeval].range(0, 10, 2).toListL.value()
    assertEquals(lst, 0.until(10, 2).toList)
  }

  test("Iterant.range(10, 0, -1)") { implicit s =>
    val lst = Iterant[Coeval].range(10, 0, -1).toListL.value()
    assertEquals(lst, 10.until(0, -1).toList)
  }

  test("Iterant.range(10, 0, -2)") { implicit s =>
    val lst = Iterant[Coeval].range(10, 0, -2).toListL.value()
    assertEquals(lst, 10.until(0, -2).toList)
  }

  test("Iterant.range(0, 10, -1)") { implicit s =>
    val lst = Iterant[Coeval].range(0, 10, -1).toListL.value()
    assertEquals(lst, 0.until(10, -1).toList)
  }

  test("Iterant.range(10, 0, 1)") { implicit s =>
    val lst = Iterant[Coeval].range(10, 0).toListL.value()
    assertEquals(lst, 10.until(0, 1).toList)
  }
}
