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
package internal

import minitest.SimpleTestSuite

object TaskCallStackSuite extends SimpleTestSuite {
  test("flatMap lookup is LIFO and skips other frames") {
    val stack = new TaskCallStack(1)
    val first: Any => Task[Any] = _ => Task.pure(1)
    val second: Any => Task[Any] = _ => Task.pure(2)

    stack.pushFlatMap(first)
    stack.pushOnCancel(Task.pure(()))
    stack.pushFlatMap(second)

    assert(stack.findAndPopNextFlatMap() eq second)
    assert(stack.findAndPopNextFlatMap() eq first)
    assertEquals(stack.findAndPopNextOnCancel(), null)
  }

  test("error lookup discards non-error frames") {
    val stack = new TaskCallStack(1)
    val handler: Throwable => Task[Any] = _ => Task.pure(1)
    val bind: Any => Task[Any] = _ => Task.pure(2)

    stack.pushHandleError(handler)
    stack.pushFlatMap(bind)
    stack.pushOnCancel(Task.pure(()))

    assert(stack.findAndPopNextHandleError() eq handler)
    assertEquals(stack.findAndPopNextFlatMap(), null)
    assertEquals(stack.findAndPopNextOnCancel(), null)
  }

  test("cancellation lookup is LIFO and skips other frames") {
    val stack = new TaskCallStack(1)
    val first = Task.pure(1).map(_ => ())
    val second = Task.pure(2).map(_ => ())

    stack.pushOnCancel(first)
    stack.pushFlatMap(_ => Task.pure(3))
    stack.pushOnCancel(second)

    assert(stack.findAndPopNextOnCancel() eq second)
    assert(stack.findAndPopNextOnCancel() eq first)
    assertEquals(stack.findAndPopNextFlatMap(), null)
  }

  test("stack grows from capacity one and preserves LIFO order") {
    val stack = new TaskCallStack(1)
    val binds = new Array[Any => Task[Any]](32)

    for (index <- binds.indices) {
      val value = index
      val bind: Any => Task[Any] = _ => Task.pure(value)
      binds(index) = bind
      stack.pushFlatMap(bind)
    }

    for (index <- binds.indices.reverse)
      assert(stack.findAndPopNextFlatMap() eq binds(index))

    assertEquals(stack.findAndPopNextFlatMap(), null)
  }
}
