/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.tail.cursors.{EmptyCursor, Generator}

object GeneratorBuildersSuite extends BaseTestSuite {
  test("apply") { _ =>
    check1 { (list: List[Int]) =>
      list == Generator(list:_*).toList
    }
  }

  test("empty") { _ =>
    val ref = Generator.empty[Int]
    assertEquals(ref.cursor(), EmptyCursor)
    assertEquals(ref.toList, List.empty)
    assert(ref.toArray.isEmpty, "toArray.isEmpty")
  }

  test("range(0, 100)") { _ =>
    val range = Generator.range(0, 100)
    assertEquals(range.toList, (0 until 100).toList)
  }

  test("range(0, 100, 2)") { _ =>
    val range = Generator.range(0, 100, 2)
    assertEquals(range.toList, 0.until(100, 2).toList)
  }

  test("range(100, 0, -1)") { _ =>
    val range = Generator.range(100, 0, -1)
    assertEquals(range.toList, 100.until(0, -1).toList)
  }
}
