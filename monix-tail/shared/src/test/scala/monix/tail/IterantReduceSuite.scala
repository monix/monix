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

object IterantReduceSuite extends BaseTestSuite {
  test("reduce is consistent with foldLeft") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], op: (Int, Int) => Int) =>
      val received = stream.reduceL(op)
      val expected = stream.foldLeftL(Option.empty[Int])((acc, e) => Some(acc.fold(e)(s => op(s, e))))
      received <-> expected
    }
  }

  test("maxL is consistent with List.max") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      val expect = if (list.isEmpty) None else Some(list.max)
      stream.maxL <-> Coeval.pure(expect)
    }
  }

  test("maxByL is consistent with List.maxBy") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      val expect = if (list.isEmpty) None else Some(list.maxBy(Math.pow(_, 2)))
      stream.maxByL(Math.pow(_, 2)) <-> Coeval.pure(expect)
    }
  }

  test("minL is consistent with List.min") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      val expect = if (list.isEmpty) None else Some(list.min)
      stream.minL <-> Coeval.pure(expect)
    }
  }

  test("minByL is consistent with List.minBy") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      val expect = if (list.isEmpty) None else Some(list.minBy(Math.pow(_, 2)))
      stream.minByL(Math.pow(_, 2)) <-> Coeval.pure(expect)
    }
  }

  test("protects against broken cursor, as first node") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Coeval].nextCursorS[Int](ThrowExceptionCursor(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .reduceL(_ + _)

    assertEquals(effect, 0)
    assertEquals(stream.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken cursor, as second node") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val source =
      Iterant[Coeval].pure(1) ++
      Iterant[Coeval].nextCursorS[Int](ThrowExceptionCursor(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)

    val stream = source
      .doOnEarlyStop(Coeval { effect += 1 })
      .reduceL(_ + _)

    assertEquals(effect, 0)
    assertEquals(stream.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken batch, as first node") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Coeval].nextBatchS[Int](ThrowExceptionBatch(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .reduceL(_ + _)

    assertEquals(effect, 0)
    assertEquals(stream.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken batch, as second node") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val source =
      Iterant[Coeval].pure(1) ++
      Iterant[Coeval].nextBatchS[Int](ThrowExceptionBatch(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)

    val stream = source
      .doOnEarlyStop(Coeval { effect += 1 })
      .reduceL(_ + _)

    assertEquals(effect, 0)
    assertEquals(stream.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken op") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Coeval].of(1, 2)
      .doOnEarlyStop(Coeval { effect += 1 })
      .reduceL((_, _) => (throw dummy) : Int)

    assertEquals(effect, 0)
    assertEquals(stream.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }
}
