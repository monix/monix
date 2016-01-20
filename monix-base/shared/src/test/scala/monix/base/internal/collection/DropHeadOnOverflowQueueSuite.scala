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

import java.util.ConcurrentModificationException
import minitest.SimpleTestSuite

object DropHeadOnOverflowQueueSuite extends SimpleTestSuite {
  test("should not accept null values") {
    val q = DropAllOnOverflowQueue[String](100)
    intercept[NullPointerException] {
      q.offer(null)
    }
  }

  test("capacity must be computed as a power of 2") {
    val q1 = DropHeadOnOverflowQueue[Int](1000)
    assertEquals(q1.capacity, 1023)

    val q2 = DropHeadOnOverflowQueue[Int](600)
    assertEquals(q2.capacity, 1023)

    val q3 = DropHeadOnOverflowQueue[Int](1024)
    assertEquals(q3.capacity, 1023)

    val q4 = DropHeadOnOverflowQueue[Int](1025)
    assertEquals(q4.capacity, 2047)

    intercept[IllegalArgumentException] {
      DropHeadOnOverflowQueue[Int](0)
    }

    intercept[IllegalArgumentException] {
      DropHeadOnOverflowQueue[Int](-100)
    }
  }

  test("offer and poll, happy path") {
    val array = new Array[Int](7)
    val q = DropHeadOnOverflowQueue[Int](7)

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

    assertEquals(q.pollMany(array), 7)
    assertEquals(array.toList, List(40, 50, 60, 70, 80, 90, 100))
  }

  test("offer and poll, overflow") {
    val array = new Array[Int](7)
    val q = DropHeadOnOverflowQueue[Int](7)

    assertEquals(q.capacity, 7)
    assert(q.poll().asInstanceOf[AnyRef] == null)

    assertEquals(q.offer(0), 0)
    assertEquals(q.poll(), 0)

    assertEquals(q.offerMany(1 to 7: _*), 0)

    assertEquals(q.offer(8), 1)
    assertEquals(q.offer(9), 1)
    assertEquals(q.offer(10), 1)
    assertEquals(q.offer(11), 1)
    assertEquals(q.offer(12), 1)
    assertEquals(q.offer(13), 1)
    assertEquals(q.offer(14), 1)

    assertEquals(q.pollMany(array), 7)
    assertEquals(array.toList, List(8, 9, 10, 11, 12, 13, 14))

    assertEquals(q.offerMany(15 until 29: _*), 7)
    assertEquals(q.pollMany(array), 7)
    assertEquals(array.toList, (22 until 29).toList)

    assert(q.poll().asInstanceOf[AnyRef] == null)
  }

  test("size should be correct on happy path") {
    val q = DropHeadOnOverflowQueue[Int](7)
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

    for (i <- 8 until 100) {
      assertEquals(q.offer(i), 1)
      assertEquals(q.size, 7)
      assert(q.isAtCapacity)
    }
  }

  test("throw ConcurrentModificationException after poll") {
    val q = DropHeadOnOverflowQueue[Int](7)
    q.offerMany(1,2,3,4)
    val iterator = q.iterator

    q.poll()
    intercept[ConcurrentModificationException] {
      iterator.hasNext
    }
  }

  test("throw ConcurrentModificationException after offer") {
    val q = DropHeadOnOverflowQueue[Int](7)
    q.offerMany(1,2,3,4)
    val iterator = q.iterator

    q.offer(1)
    intercept[ConcurrentModificationException] {
      iterator.hasNext
    }
  }

  test("isEmpty && nonEmpty && head && headOption") {
    val q = DropHeadOnOverflowQueue[Int](8)
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
    val q = DropHeadOnOverflowQueue[Int](128)
    assertEquals(q.capacity, 127)

    q.offerMany(0 until 200:_*)
    assertEquals(q.toList, 73 until 200)
  }

  test("should work with a capacity of 1") {
    val q = DropHeadOnOverflowQueue[Int](1)
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
}
