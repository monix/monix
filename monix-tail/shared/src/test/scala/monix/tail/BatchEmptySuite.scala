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
import monix.tail.batches.{Batch, EmptyBatch}

object BatchEmptySuite extends SimpleTestSuite {
  test("Batch.empty.cursor().current") {
    val cursor = Batch.empty[Int].cursor()
    intercept[NoSuchElementException] { cursor.next() }
  }

  test("Batch.empty.cursor().current after moveNext") {
    val cursor = Batch.empty[Int].cursor()
    assert(!cursor.hasNext(), "!cursor.hasNext()")
    intercept[NoSuchElementException] { cursor.next() }
  }

  test("Batch.empty.cursor().hasNext") {
    val cursor = Batch.empty[Int].cursor()
    assert(!cursor.hasNext(), "!cursor.hasMore")
  }

  test("Batch.empty.take") {
    assertEquals(Batch.empty[Int].take(1), EmptyBatch)
  }

  test("Batch.empty.drop") {
    assertEquals(Batch.empty[Int].drop(1), EmptyBatch)
  }

  test("Batch.empty.map") {
    assertEquals(Batch.empty[Int].map(x => x), EmptyBatch)
  }

  test("Batch.empty.filter") {
    assertEquals(Batch.empty[Int].filter(x => true), EmptyBatch)
  }

  test("Batch.empty.collect") {
    assertEquals(Batch.empty[Int].collect { case x => x }, EmptyBatch)
  }

  test("Batch.empty.slice") {
    assertEquals(Batch.empty[Int].slice(0, 10), EmptyBatch)
  }

  test("Batch.empty.toIterable") {
    val iter = Batch.empty[Int].toIterable.iterator
    assert(!iter.hasNext, "!iter.hasNext")
    intercept[NoSuchElementException] { iter.next() }
  }
}