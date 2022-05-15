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
import monix.eval.{ Coeval, Task }
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.tail.batches.{ Batch, BatchCursor }

import scala.util.Failure

object IterantHeadOptionSuite extends BaseTestSuite {
  test("Iterant.headOptionL <-> List.headOption") { _ =>
    check2 { (list: List[Int], idx: Int) =>
      val iter = arbitraryListToIterant[Coeval, Int](list, math.abs(idx % 4), allowErrors = false)
      iter.headOptionL <-> Coeval.now(list.headOption)
    }
  }

  test("Iterant.headOption <-> List.headOption") { _ =>
    check2 { (list: List[Int], idx: Int) =>
      val iter = arbitraryListToIterant[Coeval, Int](list, math.abs(idx % 4), allowErrors = false)
      iter.headOptionL.value() == list.headOption
    }
  }

  test("Iterant.headOption suspends execution for NextCursor or NextBatch") { _ =>
    check1 { (list: List[Int]) =>
      val iter1 = Iterant[Coeval].nextBatchS(Batch(list: _*), Coeval.now(Iterant[Coeval].empty[Int]))
      iter1.headOptionL <-> Coeval.suspend(Coeval.now(list.headOption))

      val iter2 = Iterant[Coeval].nextCursorS(BatchCursor(list: _*), Coeval.now(Iterant[Coeval].empty[Int]))
      iter2.headOptionL <-> Coeval.suspend(Coeval.now(list.headOption))
    }
  }

  test("Iterant.headOption works for empty NextCursor or NextBatch") { _ =>
    val iter1 = Iterant[Coeval].nextBatchS(Batch[Int](), Coeval.now(Iterant[Coeval].empty[Int]))
    assertEquals(iter1.headOptionL.value(), None)

    val iter2 = Iterant[Coeval].nextCursorS(BatchCursor[Int](), Coeval.now(Iterant[Coeval].empty[Int]))
    assertEquals(iter2.headOptionL.value(), None)
  }

  test("Iterant.headOption doesn't touch Halt") { implicit s =>
    val dummy = DummyException("dummy")
    val iter1: Iterant[Task, Int] = Iterant[Task].haltS(Some(dummy))
    val state1 = iter1.headOptionL
    val f = state1.runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Iterant.headOption earlyStop gets called for failing `rest` on Next node") { implicit s =>
    var effect = 0

    def stop(i: Int): Coeval[Unit] = Coeval { effect += i }
    val dummy = DummyException("dummy")
    val node3 = Iterant[Coeval].suspendS[Int](Coeval.raiseError(dummy)).guarantee(stop(3))
    val node2 = Iterant[Coeval].suspendS[Int](Coeval(node3)).guarantee(stop(2))
    val node1 = Iterant[Coeval].suspendS[Int](Coeval(node2)).guarantee(stop(1))

    assertEquals(node1.headOptionL.runTry(), Failure(dummy))
    assertEquals(effect, 6)
  }

  test("protects against broken batches as first node") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val fa = Iterant[Coeval]
      .nextBatchS[Int](ThrowExceptionBatch(dummy), Coeval(Iterant[Coeval].empty))
      .guarantee(Coeval { effect += 1 })
      .headOptionL

    assertEquals(effect, 0)
    assertEquals(fa.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("headOptionL handles Scope's release before the rest of the stream") { implicit s =>
    val triggered = Atomic(false)
    val fail = DummyException("fail")

    val lh = Iterant[Coeval].scopeS[Unit, Int](
      Coeval.unit,
      _ => Coeval(Iterant.empty),
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

    assertEquals(stream.headOptionL.value(), None)
  }

  test("headOptionL handles Scope's release after use is finished") { implicit s =>
    val triggered = Atomic(false)
    val fail = DummyException("fail")

    val stream = Iterant[Coeval].scopeS[Unit, Int](
      Coeval.unit,
      _ =>
        Coeval(Iterant.empty ++ Iterant[Coeval].suspend {
          if (triggered.getAndSet(true))
            Iterant[Coeval].raiseError[Int](fail)
          else
            Iterant[Coeval].empty[Int]
        }),
      (_, _) => {
        Coeval(triggered.set(true))
      }
    )

    assertEquals((stream ++ Iterant[Coeval].empty[Int]).headOptionL.value(), None)
  }
}
