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

import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import monix.tail.batches.{Batch, BatchCursor}

object IterantRepeatSuite extends BaseTestSuite {
  test("Iterant.repeat terminates on exception") { implicit s =>
    var effect = 0
    var values = List[Int]()
    val expectedValues = List.fill(6)(1)
    val dummy = DummyException("dummy")
    val source = Iterant[Coeval].nextS(1, Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)

    intercept[DummyException] {
      source.repeat.map { x => if (effect == 6) throw dummy else {
        effect += 1; values ::= x; x
      }}.toListL.value}

    assertEquals(values, expectedValues)
  }

  test("Iterant.repeat works for NextBatch") { implicit s =>
    var effect = 0
    var values = List[Int]()
    val expectedValues = List(3, 2, 1, 3, 2, 1)
    val dummy = DummyException("dummy")
    val source = Iterant[Coeval].nextBatchS(Batch(1, 2, 3), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)

    intercept[DummyException] {
      source.repeat.map { x => if (effect == 6) throw dummy else {
        effect += 1; values ::= x; x
      }}.toListL.value}

    assertEquals(values, expectedValues)
  }

  test("Iterant.repeat works for NextCursor") { implicit s =>
    var effect = 0
    var values = List[Int]()
    val expectedValues = List(3, 2, 1, 3, 2, 1)
    val dummy = DummyException("dummy")
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)

    intercept[DummyException] {
      source.repeat.map { x => if (effect == 6) throw dummy else {
        effect += 1; values ::= x; x
      }}.toListL.value}

    assertEquals(values, expectedValues)
  }

  test("Iterant.repeat works for Last") { implicit s =>
    var effect = 0
    var values = List[Int]()
    val expectedValues = List.fill(6)(1)
    val dummy = DummyException("dummy")
    val source = Iterant[Coeval].lastS(1)

    intercept[DummyException] {
      source.repeat.map { x => if (effect == 6) throw dummy else {
        effect += 1; values ::= x; x
      }}.toListL.value}

    assertEquals(values, expectedValues)
  }

  test("Iterant.repeat terminates if the source is empty") { implicit s =>
    val source = Iterant[Coeval].empty[Int]

    assertEquals(source.repeat, source)
  }

  test("Iterant.repeat builder terminates on exception") { implicit s =>
    var effect = 0
    var values = List[Int]()
    val expectedValues = List.fill(6)(1)
    val dummy = DummyException("dummy")
    val source = Iterant[Coeval].repeat(1)

    intercept[DummyException] {
      source.repeat.map { x => if (effect == 6) throw dummy else {
        effect += 1; values ::= x; x
      }}.toListL.value}

    assertEquals(values, expectedValues)
  }

  test("Iterant.repeat builder works for batches of elems") { implicit s =>
    var effect = 0
    var values = List[Int]()
    val expectedValues = List(3, 2, 1, 3, 2, 1)
    val dummy = DummyException("dummy")
    val source = Iterant[Coeval].repeat(List(1, 2, 3): _*)

    intercept[DummyException] {
      source.repeat.map { x => if (effect == 6) throw dummy else {
        effect += 1; values ::= x; x
      }}.toListL.value}

    assertEquals(values, expectedValues)
  }

  test("Iterant.repeat builder terminates if the source is empty") { implicit s =>
    val source = Iterant[Coeval].empty[Int]

    assertEquals(source.repeat, source)
  }
}