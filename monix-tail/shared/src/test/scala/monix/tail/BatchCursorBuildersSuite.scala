/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import monix.tail.batches.BatchCursor

object BatchCursorBuildersSuite extends BaseTestSuite {
  test("apply") { _ =>
    check1 { (list: List[Int]) =>
      list == BatchCursor(list: _*).toList &&
      list == BatchCursor.fromSeq(list).toList &&
      list == BatchCursor.fromIndexedSeq(list.toIndexedSeq).toList
    }
  }

  test("range(0, 100)") { _ =>
    val range = BatchCursor.range(0, 100)
    assertEquals(range.toList, (0 until 100).toList)
  }

  test("range(0, 100, 2)") { _ =>
    val range = BatchCursor.range(0, 100, 2)
    assertEquals(range.toList, 0.until(100, 2).toList)
  }

  test("range(100, 0, -1)") { _ =>
    val range = BatchCursor.range(100, 0, -1)
    assertEquals(range.toList, 100.until(0, -1).toList)
  }
}
