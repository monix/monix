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

package monix.execution.internal.collection

import monix.execution.BaseTestSuite

class ChunkedArrayStackSuite extends BaseTestSuite {
  test("push and pop 8 items") {
    val stack = ChunkedArrayStack[Int](chunkSize = 8)
    var times = 0

    while (times < 10) {
      assert(stack.isEmpty, "stack.isEmpty")
      for (i <- 0 until 8) stack.push(i)

      var list = List.empty[Int]
      while (!stack.isEmpty) {
        assert(!stack.isEmpty, "!stack.isEmpty")
        list = stack.pop() :: list
      }

      assertEquals(list, (0 until 8).toList)
      assertEquals(Option(stack.pop()), None)
      assert(stack.isEmpty, "stack.isEmpty")

      times += 1
    }
  }

  test("push and pop 100 items") {
    val stack = ChunkedArrayStack[Int](chunkSize = 8)
    var times = 0

    while (times < 10) {
      assert(stack.isEmpty, "stack.isEmpty")
      for (i <- 0 until 100) stack.push(i)

      var list = List.empty[Int]
      while (!stack.isEmpty) {
        assert(!stack.isEmpty, "!stack.isEmpty")
        list = stack.pop() :: list
      }

      assertEquals(list, (0 until 100).toList)
      assertEquals(Option(stack.pop()), None)
      assert(stack.isEmpty, "stack.isEmpty")

      times += 1
    }
  }

  test("pushAll(stack)") {
    val stack = ChunkedArrayStack[Int](chunkSize = 8)
    val stack2 = ChunkedArrayStack[Int](chunkSize = 8)

    for (i <- 0 until 100) stack2.push(i)
    stack.pushAll(stack2)

    var list = List.empty[Int]
    while (!stack.isEmpty) {
      assert(!stack.isEmpty)
      list = stack.pop() :: list
    }

    assertEquals(list, (0 until 100).toList.reverse)
    assertEquals(Option(stack.pop()), None)
    assert(stack.isEmpty, "stack.isEmpty")
    assert(!stack2.isEmpty, "!stack2.isEmpty")
  }

  test("pushAll(iterable)") {
    val stack = ChunkedArrayStack[Int](chunkSize = 8)
    val expected = (0 until 100).toList
    stack.pushAll(expected)

    var list = List.empty[Int]
    while (!stack.isEmpty) {
      assert(!stack.isEmpty)
      list = stack.pop() :: list
    }

    assertEquals(list, expected)
    assertEquals(Option(stack.pop()), None)
    assert(stack.isEmpty, "stack.isEmpty")
  }

  test("iterator") {
    val stack = ChunkedArrayStack[Int](chunkSize = 8)
    val expected = (0 until 100).toList
    for (i <- expected) stack.push(i)
    assertEquals(stack.iteratorReversed.toList, expected.reverse)
  }
}
