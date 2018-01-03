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

package monix.execution.internal.collection

import minitest.SimpleTestSuite

object ArrayStackSuite extends SimpleTestSuite {
  test("ArrayStack(1) push and pop") {
    val stack = ArrayStack[Int](1)
    assertEquals(stack.currentCapacity, 1)

    stack.push(1)
    assertEquals(stack.currentCapacity, 1)
    assertEquals(stack.pop(), 1)
    assertEquals(stack.currentCapacity, 1)

    stack.push(2)
    assertEquals(stack.currentCapacity, 1)
    assertEquals(stack.pop(), 2)
    assertEquals(stack.currentCapacity, 1)

    stack.push(3)
    assertEquals(stack.currentCapacity, 1)
    assertEquals(stack.pop(), 3)
    assertEquals(stack.currentCapacity, 1)

    assertEquals(stack.pop().asInstanceOf[AnyRef], null)
  }

  test("ArrayStack(1) unlimited push and pop") {
    val stack = ArrayStack[Int](1)
    assert(stack.isEmpty, "stack.isEmpty")
    assertEquals(stack.currentCapacity, 1)
    assertEquals(stack.minimumCapacity, 1)

    for (i <- 0 until 256) stack.push(i)
    assertEquals(stack.currentCapacity, 256)
    assertEquals(stack.size, 256)
    assert(!stack.isEmpty, "!stack.isEmpty")

    stack.push(256)
    assertEquals(stack.size, 257)
    assertEquals(stack.currentCapacity, 512)
    assert(!stack.isEmpty, "!stack.isEmpty")

    for (i <- 256.until(128, -1)) assertEquals(stack.pop(), i)
    assertEquals(stack.currentCapacity, 512)
    assertEquals(stack.size, 129)
    assert(!stack.isEmpty, "!stack.isEmpty")

    // should shrink
    assertEquals(stack.pop(), 128)
    assertEquals(stack.size, 128)
    assertEquals(stack.currentCapacity, 256)
    assert(!stack.isEmpty, "!stack.isEmpty")

    for (i <- 127.to(0, -1)) assertEquals(stack.pop(), i)
    assertEquals(stack.size, 0)
    assertEquals(stack.currentCapacity, 1)
    assert(stack.isEmpty, "stack.isEmpty")

    assertEquals(stack.pop().asInstanceOf[AnyRef], null)
  }

  test("ArrayStack(16) unlimited push and pop") {
    val stack = ArrayStack[Int](16)
    assert(stack.isEmpty, "stack.isEmpty")
    assertEquals(stack.currentCapacity, 16)
    assertEquals(stack.minimumCapacity, 16)

    for (i <- 0 until 256) stack.push(i)
    assertEquals(stack.currentCapacity, 256)
    assertEquals(stack.size, 256)
    assert(!stack.isEmpty, "!stack.isEmpty")

    stack.push(256)
    assertEquals(stack.size, 257)
    assertEquals(stack.currentCapacity, 512)
    assert(!stack.isEmpty, "!stack.isEmpty")

    for (i <- 256.until(128, -1)) assertEquals(stack.pop(), i)
    assertEquals(stack.currentCapacity, 512)
    assertEquals(stack.size, 129)
    assert(!stack.isEmpty, "!stack.isEmpty")

    // should shrink
    assertEquals(stack.pop(), 128)
    assertEquals(stack.size, 128)
    assertEquals(stack.currentCapacity, 256)
    assert(!stack.isEmpty, "!stack.isEmpty")

    for (i <- 127.to(0, -1)) assertEquals(stack.pop(), i)
    assertEquals(stack.size, 0)
    assertEquals(stack.currentCapacity, 16)
    assert(stack.isEmpty, "stack.isEmpty")

    assertEquals(stack.pop().asInstanceOf[AnyRef], null)
  }

  test("ArrayStack clone") {
    val stack = ArrayStack[Int](16)
    assert(stack.isEmpty, "stack.isEmpty")
    for (i <- 0 until 256) stack.push(i)

    val cloned = stack.clone()
    assertEquals(cloned.minimumCapacity, 16)
    assertEquals(cloned.currentCapacity, 256)
    assertEquals(cloned.size, 256)
    for (i <- 255.to(0, -1)) assertEquals(cloned.pop(), i)
  }

  test("ArrayStack grows and shrinks") {
    val stack = ArrayStack[Int](8)
    val count = 256

    assertEquals(stack.minimumCapacity, 8)
    assertEquals(stack.currentCapacity, 8)

    for (i <- 1 to count) {
      stack.push(i)
    }

    assertEquals(stack.currentCapacity, count)
    assertEquals(stack.minimumCapacity, 8)

    var sum = 0
    while (!stack.isEmpty) {
      sum += stack.pop()
    }

    assertEquals(sum, count * (count + 1) / 2)
    assertEquals(stack.currentCapacity, 8)
    assertEquals(stack.minimumCapacity, 8)
  }
}
