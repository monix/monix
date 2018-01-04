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

import cats.laws._
import cats.laws.discipline._
import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import scala.util.Failure

object IterantDistinctUntilChangedSuite extends BaseTestSuite {
  test("suppresses duplicates") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val expected = if (list.isEmpty) Nil else {
        list.head +: list.tail.zip(list).filter { case (a, b) => a != b }.map(_._1)
      }

      val received = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
        .distinctUntilChanged
        .toListL

      received <-> Coeval.pure(expected)
    }
  }

  test("suppresses duplicates by key") { implicit s =>
    check3 { (list: List[Int], idx: Int, f: Int => Int) =>
      val expected = if (list.isEmpty) Nil else {
        list.head +: list.tail.zip(list).filter { case (a, b) => f(a) != f(b) }.map(_._1)
      }

      val received = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
        .distinctUntilChangedByKey(f)
        .toListL

      received <-> Coeval.pure(expected)
    }
  }

  test("protects against broken function") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      var effect = 0
      val dummy = DummyException("dummy")

      val received = (arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false) ++ Iterant[Coeval].of(1, 2))
        .doOnEarlyStop(Coeval { effect += 111 })
        .distinctUntilChangedByKey(_ => (throw dummy) : Int)
        .completeL.map(_ => 0)
        .onErrorRecover { case _: DummyException => effect }

      received <->  Coeval.pure(111)
    }
  }

  test("protects against broken cursors as first node") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val fa = Iterant[Coeval].nextCursorS[Int](ThrowExceptionCursor(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .distinctUntilChanged
      .completeL

    assertEquals(effect, 0)
    assertEquals(fa.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken cursors as second node") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val stream = Iterant[Coeval].pure(1) ++
      Iterant[Coeval].nextCursorS[Int](ThrowExceptionCursor(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)

    val fa = stream
      .doOnEarlyStop(Coeval { effect += 1 })
      .distinctUntilChanged
      .completeL

    assertEquals(effect, 0)
    assertEquals(fa.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken batches as first node") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val fa = Iterant[Coeval].nextBatchS[Int](ThrowExceptionBatch(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .distinctUntilChanged
      .completeL

    assertEquals(effect, 0)
    assertEquals(fa.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken batches as second node") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val stream = Iterant[Coeval].pure(1) ++
      Iterant[Coeval].nextBatchS[Int](ThrowExceptionBatch(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)

    val fa = stream
      .doOnEarlyStop(Coeval { effect += 1 })
      .distinctUntilChanged
      .completeL

    assertEquals(effect, 0)
    assertEquals(fa.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }
}
