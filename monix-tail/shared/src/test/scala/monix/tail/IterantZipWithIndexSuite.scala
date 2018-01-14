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
import monix.eval.{Coeval, Task}
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.tail.batches.{Batch, BatchCursor}
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

object IterantZipWithIndexSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  test("Iterant.zipWithIndex equivalence with List.zipWithIndex") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val stream = arbitraryListToIterant[Coeval, Int](list, math.abs(idx) + 1, allowErrors = false)
      val received = stream.zipWithIndex.toListL
      val expected = Coeval(list.zipWithIndex.map { case (a, b) => (a, b.toLong) })

      received <-> expected
    }
  }

  test("Iterant[Task].zipWithIndex works for non-determinate batches") { implicit s =>
    check2 { (list: List[Int], _: Int) =>
      val stream = Iterant[Task].nextBatchS(Batch.fromIterable(list, 1), Task.now(Iterant[Task].empty[Int]), Task.unit)
      stream.zipWithIndex.toListL <-> stream.toListL.map(_.zipWithIndex.map { case (a, b) => (a, b.toLong) })
    }
  }

  test("Iterant.zipWithIndex works for infinite cursors") { implicit s =>
    check2 { (el: Int, _: Int) =>
      val stream = Iterant[Coeval].nextCursorS(BatchCursor.continually(el), Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val received = stream.zipWithIndex.take(1).toListL
      val expected = Coeval(Stream.continually(el).zipWithIndex.map { case (a, b) => (a, b.toLong) }.take(1).toList)

      received <-> expected
    }
  }

  test("Iterant.zipWithIndex protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.zipWithIndex
      received <-> Iterant[Task].haltS[(Int, Long)](Some(dummy))
    }
  }

  test("Iterant.zipWithIndex protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.zipWithIndex
      received <-> Iterant[Task].haltS[(Int, Long)](Some(dummy))
    }
  }

  test("Iterant.zipWithIndex triggers early stop on exception") { _ =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      val cancelable = BooleanCancelable()
      val dummy = DummyException("dummy")
      val suffix = Iterant[Coeval].nextCursorS[Int](new ThrowExceptionCursor(dummy), Coeval.now(Iterant[Coeval].empty), Coeval.unit)
      val stream = (iter.onErrorIgnore ++ suffix).doOnEarlyStop(Coeval.eval(cancelable.cancel()))

      intercept[DummyException] {
        stream.zipWithIndex.toListL.value
      }
      cancelable.isCanceled
    }
  }

  test("Iterant.zipWithIndex preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source.zipWithIndex

    stream.earlyStop.value
    assertEquals(effect, 1)
  }
}
