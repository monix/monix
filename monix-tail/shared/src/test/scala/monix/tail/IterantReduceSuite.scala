/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.tail.batches.Batch
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
      val expect = if (list.isEmpty) None else Some(list.maxBy(v => Math.pow(v.toDouble, 2)))
      stream.maxByL(v => Math.pow(v.toDouble, 2)) <-> Coeval.pure(expect)
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
      val expect = if (list.isEmpty) None else Some(list.minBy(v => Math.pow(v.toDouble, 2)))
      stream.minByL(v => Math.pow(v.toDouble, 2)) <-> Coeval.pure(expect)
    }
  }

  test("protects against broken cursor, as first node") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Coeval]
      .nextCursorS[Int](ThrowExceptionCursor(dummy), Coeval(Iterant[Coeval].empty))
      .guarantee(Coeval { effect += 1 })
      .reduceL(_ + _)

    assertEquals(effect, 0)
    assertEquals(stream.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken cursor, as second node") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val source =
      Iterant[Coeval].pure(1) ++
        Iterant[Coeval].nextCursorS[Int](ThrowExceptionCursor(dummy), Coeval(Iterant[Coeval].empty))

    val stream = source
      .guarantee(Coeval { effect += 1 })
      .reduceL(_ + _)

    assertEquals(effect, 0)
    assertEquals(stream.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken batch, as first node") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Coeval]
      .nextBatchS[Int](ThrowExceptionBatch(dummy), Coeval(Iterant[Coeval].empty))
      .guarantee(Coeval { effect += 1 })
      .reduceL(_ + _)

    assertEquals(effect, 0)
    assertEquals(stream.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken batch, as second node") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val source =
      Iterant[Coeval].pure(1) ++
        Iterant[Coeval].nextBatchS[Int](ThrowExceptionBatch(dummy), Coeval(Iterant[Coeval].empty))

    val stream = source
      .guarantee(Coeval { effect += 1 })
      .reduceL(_ + _)

    assertEquals(effect, 0)
    assertEquals(stream.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protects against broken op") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Coeval]
      .of(1, 2)
      .guarantee(Coeval { effect += 1 })
      .reduceL((_, _) => (throw dummy): Int)

    assertEquals(effect, 0)
    assertEquals(stream.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("resources are released for failing `rest` on Next node") { implicit s =>
    var effect = 0

    def stop(i: Int): Coeval[Unit] = Coeval { effect += i }
    val dummy = DummyException("dummy")
    val node3 = Iterant[Coeval].nextS(3, Coeval.raiseError(dummy)).guarantee(stop(3))
    val node2 = Iterant[Coeval].nextS(2, Coeval(node3)).guarantee(stop(2))
    val node1 = Iterant[Coeval].nextS(1, Coeval(node2)).guarantee(stop(1))

    assertEquals(node1.reduceL((_, el) => el).runTry(), Failure(dummy))
    assertEquals(effect, 6)
  }

  test("resources are released for failing `rest` on NextBatch node") { implicit s =>
    var effect = 0

    def stop(i: Int): Coeval[Unit] = Coeval { effect += i }
    val dummy = DummyException("dummy")
    val node3 = Iterant[Coeval].nextBatchS(Batch(1, 2, 3), Coeval.raiseError(dummy)).guarantee(stop(3))
    val node2 = Iterant[Coeval].nextBatchS(Batch(1, 2, 3), Coeval(node3)).guarantee(stop(2))
    val node1 = Iterant[Coeval].nextBatchS(Batch(1, 2, 3), Coeval(node2)).guarantee(stop(1))

    assertEquals(node1.reduceL((_, el) => el).runTry(), Failure(dummy))
    assertEquals(effect, 6)
  }

  test("reduceL handles Scope's release before the rest of the stream") { implicit s =>
    val triggered = Atomic(false)
    val fail = DummyException("fail")

    val lh = Iterant[Coeval].scopeS[Unit, Int](
      Coeval.unit,
      _ => Coeval(Iterant.pure(1)),
      (_, _) => Coeval(triggered.set(true))
    )

    val stream = Iterant[Coeval].concatS(
      Coeval(lh),
      Coeval {
        if (!triggered.getAndSet(true))
          Iterant[Coeval].raiseError[Int](fail)
        else
          Iterant[Coeval].empty[Int]
      }
    )

    assertEquals(stream.reduceL(_ + _).value(), Some(1))
  }

  test("reduceL handles Scope's release after use is finished") { implicit s =>
    val triggered = Atomic(false)
    val fail = DummyException("fail")

    val stream = Iterant[Coeval].scopeS[Unit, Int](
      Coeval.unit,
      _ =>
        Coeval(2 +: Iterant[Coeval].suspend {
          if (triggered.getAndSet(true))
            Iterant[Coeval].raiseError[Int](fail)
          else
            Iterant[Coeval].empty[Int]
        }),
      (_, _) => {
        Coeval(triggered.set(true))
      }
    )

    assertEquals((1 +: stream :+ 3).reduceL(_ + _).value(), Some(6))
  }
}
