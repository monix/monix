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

package monix.tail

import java.util.NoSuchElementException

import minitest.SimpleTestSuite
import monix.tail.batches.{BatchCursor, EmptyCursor}

object BatchCursorEmptySuite extends SimpleTestSuite {
  test("BatchCursor.empty.current") {
    val cursor = BatchCursor.empty[Int]
    intercept[NoSuchElementException] { cursor.next() }
  }

  test("BatchCursor.empty.current after moveNext") {
    val cursor = BatchCursor.empty[Int]
    assert(!cursor.hasNext(), "!cursor.hasNext()")
    intercept[NoSuchElementException] { cursor.next() }
  }

  test("BatchCursor.empty.hasNext") {
    val cursor = BatchCursor.empty[Int]
    assert(!cursor.hasNext(), "!cursor.hasMore")
  }

  test("BatchCursor.empty.take") {
    assertEquals(BatchCursor.empty[Int].take(1), EmptyCursor)
  }

  test("BatchCursor.empty.drop") {
    assertEquals(BatchCursor.empty[Int].drop(1), EmptyCursor)
  }

  test("BatchCursor.empty.map") {
    assertEquals(BatchCursor.empty[Int].map(x => x), EmptyCursor)
  }

  test("BatchCursor.empty.filter") {
    assertEquals(BatchCursor.empty[Int].filter(x => true), EmptyCursor)
  }

  test("BatchCursor.empty.collect") {
    assertEquals(BatchCursor.empty[Int].collect { case x => x }, EmptyCursor)
  }

  test("BatchCursor.empty.slice") {
    assertEquals(BatchCursor.empty[Int].slice(0, 10), EmptyCursor)
  }

  test("BatchCursor.empty.toIterator") {
    val iter = BatchCursor.empty[Int].toIterator
    assert(!iter.hasNext, "!iter.hasNext")
    intercept[NoSuchElementException] { iter.next() }
  }
}