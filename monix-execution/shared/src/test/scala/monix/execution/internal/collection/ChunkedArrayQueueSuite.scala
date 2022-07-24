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
import scala.collection.immutable.Queue

class ChunkedArrayQueueSuite extends BaseTestSuite {
  test("enqueue and dequeue 8 items") {
    val queue = ChunkedArrayQueue[Int](chunkSize = 8)
    var times = 0

    while (times < 10) {
      assert(queue.isEmpty, "queue.isEmpty")
      for (i <- 0 until 8) queue.enqueue(i)

      var list = List.empty[Int]
      while (!queue.isEmpty) {
        assert(!queue.isEmpty, "!queue.isEmpty")
        list = queue.dequeue() :: list
      }

      assertEquals(list, (0 until 8).reverse.toList)
      assertEquals(Option(queue.dequeue()), None)
      assert(queue.isEmpty, "queue.isEmpty")

      times += 1
    }
  }

  test("enqueue and dequeue 100 items") {
    val queue = ChunkedArrayQueue[Int](chunkSize = 8)
    var times = 0

    while (times < 10) {
      assert(queue.isEmpty, "queue.isEmpty")
      for (i <- 0 until 100) queue.enqueue(i)

      var list = List.empty[Int]
      while (!queue.isEmpty) {
        assert(!queue.isEmpty, "!queue.isEmpty")
        list = queue.dequeue() :: list
      }

      assertEquals(list, (0 until 100).reverse.toList)
      assertEquals(Option(queue.dequeue()), None)
      assert(queue.isEmpty, "queue.isEmpty")

      times += 1
    }
  }

  test("enqueue/dequeue on each step") {
    val queue = ChunkedArrayQueue[AnyRef](chunkSize = 8)

    for (i <- 0 until 100) {
      queue.enqueue(i.asInstanceOf[AnyRef])
      assertEquals(queue.dequeue().asInstanceOf[Int], i)
      assertEquals(Option(queue.dequeue()), None)
    }
  }

  test("enqueueAll(queue)") {
    val queue = ChunkedArrayQueue[Int](chunkSize = 8)
    val queue2 = ChunkedArrayQueue[Int](chunkSize = 8)

    for (i <- 0 until 100) queue2.enqueue(i)
    queue.enqueueAll(queue2)

    var list = Queue.empty[Int]
    while (!queue.isEmpty) {
      assert(!queue.isEmpty)
      list = list.enqueue(queue.dequeue())
    }

    assertEquals(list.toList, (0 until 100).toList)
    assertEquals(Option(queue.dequeue()), None)
    assert(queue.isEmpty, "queue.isEmpty")
    assert(!queue2.isEmpty, "!stack2.isEmpty")
  }

  test("enqueueAll(iterable)") {
    val queue = ChunkedArrayQueue[Int](chunkSize = 8)
    val expected = (0 until 100).toList
    queue.enqueueAll(expected)

    var list = Queue.empty[Int]
    while (!queue.isEmpty) {
      assert(!queue.isEmpty)
      list = list.enqueue(queue.dequeue())
    }

    assertEquals(list.toList, expected)
    assertEquals(Option(queue.dequeue()), None)
    assert(queue.isEmpty, "queue.isEmpty")
  }

  test("iterator") {
    val queue = ChunkedArrayQueue[Int](chunkSize = 8)
    val expected = (0 until 100).toList
    for (i <- expected) queue.enqueue(i)

    assertEquals(queue.dequeue(), 0)
    assertEquals(queue.dequeue(), 1)
    assertEquals(queue.iterator.toList, expected.drop(2))
  }
}
