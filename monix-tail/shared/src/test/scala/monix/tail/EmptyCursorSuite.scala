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

import minitest.SimpleTestSuite
import monix.tail.cursors.EmptyCursor
import monix.tail.exceptions.CursorIsEmptyException

object EmptyCursorSuite extends SimpleTestSuite {
  test("Cursor.empty.current") {
    val cursor = Cursor.empty[Int]
    intercept[CursorIsEmptyException] { cursor.current }
  }

  test("Cursor.empty.current after moveNext") {
    val cursor = Cursor.empty[Int]
    assert(!cursor.moveNext(), "!cursor.moveNext()")
    intercept[CursorIsEmptyException] { cursor.current }
  }

  test("Cursor.empty.hasMore") {
    val cursor = Cursor.empty[Int]
    assert(!cursor.hasMore(), "!cursor.hasMore")
    assert(!cursor.moveNext(), "!cursor.moveNext()")
    assert(!cursor.hasMore(), "!cursor.hasMore")
  }

  test("Cursor.empty.take") {
    assertEquals(Cursor.empty[Int].take(1), EmptyCursor)
  }

  test("Cursor.empty.drop") {
    assertEquals(Cursor.empty[Int].drop(1), EmptyCursor)
  }

  test("Cursor.empty.map") {
    assertEquals(Cursor.empty[Int].map(x => x), EmptyCursor)
  }

  test("Cursor.empty.filter") {
    assertEquals(Cursor.empty[Int].filter(x => true), EmptyCursor)
  }

  test("Cursor.empty.collect") {
    assertEquals(Cursor.empty[Int].collect { case x => x }, EmptyCursor)
  }

  test("Cursor.empty.slice") {
    assertEquals(Cursor.empty[Int].slice(0, 10), EmptyCursor)
  }

  test("Cursor.empty.toIterator") {
    val iter = Cursor.empty[Int].toIterator
    assert(!iter.hasNext, "!iter.hasNext")
    intercept[NoSuchElementException] { iter.next() }
  }
}
