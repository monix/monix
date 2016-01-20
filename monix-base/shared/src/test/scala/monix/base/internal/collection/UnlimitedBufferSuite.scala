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
 *
 */

package monix.base.internal.collection

import minitest.SimpleTestSuite

object UnlimitedBufferSuite extends SimpleTestSuite {
  test("should grow dynamically") {
    val b = UnlimitedBuffer[Int]()
    for (i <- 0 until 1000) b.offer(i)

    assertEquals(b.toList, (0 until 1000).toList)
  }

  test("should work at limit") {
    val b = UnlimitedBuffer[Int]()
    for (i <- 0 until (1024 - 16)) b.offer(i)

    assertEquals(b.toList, (0 until (1024 - 16)).toList)
  }

  test("should offer many") {
    val b = UnlimitedBuffer[Int]()
    b.offerMany(0 until 1000:_*)
    assertEquals(b.toList, 0 until 1000)
  }

  test("should clear") {
    val b = UnlimitedBuffer[Int]()
    b.offerMany(0 until 1000:_*)
    b.clear()

    assertEquals(b.length, 0)
    assert(b.isEmpty)
    assertEquals(b.toList, Nil)

    b.offerMany(1000 until 2000:_*)
    assertEquals(b.toList, 1000 until 2000)
  }

  test("custom initial capacity") {
    val b = UnlimitedBuffer[Int](1000)
    b.offerMany(0 until 500:_*)
    assertEquals(b.toList, 0 until 500)
    b.offerMany(500 until 1000:_*)
    assertEquals(b.toList, 0 until 1000)
  }
}
