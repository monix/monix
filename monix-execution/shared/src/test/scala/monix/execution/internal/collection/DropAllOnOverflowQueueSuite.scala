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
import scala.collection.mutable.ListBuffer

object DropAllOnOverflowQueueSuite extends SimpleTestSuite {
  test("should not accept null values") {
    val q = DropAllOnOverflowQueue[String](100)
    intercept[NullPointerException] {
      q.offer(null)
    }
  }

  test("capacity must be computed as a power of 2") {
    val q1 = DropAllOnOverflowQueue[Int](1000)
    assertEquals(q1.capacity, 1023)

    val q2 = DropAllOnOverflowQueue[Int](600)
    assertEquals(q2.capacity, 1023)

    val q3 = DropAllOnOverflowQueue[Int](1024)
    assertEquals(q3.capacity, 2047)

    val q4 = DropAllOnOverflowQueue[Int](1025)
    assertEquals(q4.capacity, 2047)

    intercept[IllegalArgumentException] {
      DropAllOnOverflowQueue[Int](0)
    }

    intercept[IllegalArgumentException] {
      DropAllOnOverflowQueue[Int](-100)
    }
  }

  test("offer and poll with drainToArray, happy path") {
    val array = new Array[Int](7)
    val q = DropAllOnOverflowQueue[Int](7)

    assertEquals(q.capacity, 7)
    assert(q.poll().asInstanceOf[AnyRef] == null)

    assertEquals(q.offer(10), 0)
    assertEquals(q.offer(20), 0)
    assertEquals(q.offer(30), 0)

    assertEquals(q.poll(), 10)
    assertEquals(q.poll(), 20)
    assertEquals(q.poll(), 30)
    assert(q.poll().asInstanceOf[AnyRef] == null)

    assertEquals(q.offerMany(40, 50, 60, 70, 80, 90, 100), 0)

    val buffer = ListBuffer.empty[Int]
    assertEquals(q.drainToBuffer(buffer, 3), 3)
    assertEquals(buffer.toList, List(40, 50, 60))

    assertEquals(q.drainToArray(array), 4)
    assertEquals(array.toList.take(4), List(70, 80, 90, 100))
  }

  test("offer and poll, overflow") {
    val array = new Array[Int](7)
    val q = DropAllOnOverflowQueue[Int](7)

    assertEquals(q.capacity, 7)
    assert(q.poll().asInstanceOf[AnyRef] == null)

    assertEquals(q.offer(0), 0)
    assertEquals(q.poll(), 0)

    assertEquals(q.offerMany(1 to 7: _*), 0)

    assertEquals(q.offer(8), 7)
    assertEquals(q.offerMany(9 to 14: _*), 0)

    val buffer = ListBuffer.empty[Int]
    assertEquals(q.drainToBuffer(buffer, 3), 3)
    assertEquals(buffer.toList, List(8, 9, 10))

    assertEquals(q.drainToArray(array), 4)
    assertEquals(array.toList.take(4), List(11, 12, 13, 14))

    assertEquals(q.offerMany(15 until 29: _*), 7)
    assertEquals(q.drainToArray(array), 7)
    assertEquals(array.toList, (22 until 29).toList)

    assert(q.poll().asInstanceOf[AnyRef] == null)
  }

  test("size should be correct on happy path") {
    val q = DropAllOnOverflowQueue[Int](7)
    assertEquals(q.size, 0)

    for (i <- 1 to 7) {
      assertEquals(q.offer(i), 0)
      assertEquals(q.size, i)
    }

    assert(q.isAtCapacity)
    for (i <- 1 to 5) {
      assertEquals(q.poll(), i)
      assertEquals(q.size, 7 - i)
      assert(!q.isAtCapacity)
    }

    assertEquals(q.offer(1), 0)
    assertEquals(q.size, 3)
    assertEquals(q.offer(2), 0)
    assertEquals(q.size, 4)
    assertEquals(q.offer(3), 0)
    assertEquals(q.size, 5)
    assertEquals(q.offer(4), 0)
    assertEquals(q.size, 6)
    assertEquals(q.offer(7), 0)
    assertEquals(q.size, 7)
    assert(q.isAtCapacity)

    for (i <- 0 until 100) {
      if (i % 7 == 0) {
        assert(q.isAtCapacity)
        assertEquals(q.offer(i), 7)
        assertEquals(q.size, 1)
        assert(!q.isAtCapacity)
      }
      else {
        assertEquals(q.offer(i), 0)
        assertEquals(q.size, i % 7 + 1)
      }
    }
  }

  test("isEmpty && nonEmpty && head && headOption") {
    val q = DropAllOnOverflowQueue[Int](8)
    assert(q.isEmpty)
    assert(!q.nonEmpty)

    intercept[NoSuchElementException](q.head)
    assertEquals(q.headOption, None)

    q.offer(1)
    assert(!q.isEmpty)
    assert(q.nonEmpty)

    assertEquals(q.head, 1)
    assertEquals(q.headOption, Some(1))

    q.poll()
    assert(q.isEmpty)
    assert(!q.nonEmpty)

    intercept[NoSuchElementException](q.head)
    assertEquals(q.headOption, None)
  }

  test("iterable") {
    val q = DropAllOnOverflowQueue[Int](127)
    q.offerMany(0 until 200:_*)
    assertEquals(q.toList, 127 until 200)
  }

  test("should work with a capacity of 1") {
    val q = DropAllOnOverflowQueue[Int](1)
    assert(q.isEmpty)

    q.offerMany(0 until 10:_*)
    assertEquals(q.head, 9)
    assertEquals(q.length, 1)

    q.offerMany(10 until 20:_*)
    assertEquals(q.head, 19)
    assertEquals(q.length, 1)

    q.offerMany(20 until 30:_*)
    assertEquals(q.head, 29)
    assertEquals(q.length, 1)
    assertEquals(q.poll(), 29)
    assertEquals(q.length, 0)
  }

  test("should iterate with fixed capacity") {
    val q = DropAllOnOverflowQueue[Int](10)
    q.offerMany(0 until 15:_*)

    val list1 = q.iterator(exactSize = false).toList
    assertEquals(list1.length, 15)
    assertEquals(list1, (0 until 15).toList)

    val list2 = q.iterator(exactSize = true).toList
    assertEquals(list2.length, 10)
    assertEquals(list2, (5 until 15).toList)
  }

  test("should have at least the recommended capacity") {
    val q1 = DropAllOnOverflowQueue[Int](16)
    assertEquals(q1.length, 0)
    assertEquals(q1.capacity, 31)

    val q2 = DropAllOnOverflowQueue[Int](15)
    assertEquals(q2.length, 0)
    assertEquals(q2.capacity, 15)
  }

  test("should box") {
    val q = DropAllOnOverflowQueue.boxed[Int](10)
    q.offerMany(0 until 15:_*)
    assertEquals(q.toList, (0 until 15).toList)
  }
}