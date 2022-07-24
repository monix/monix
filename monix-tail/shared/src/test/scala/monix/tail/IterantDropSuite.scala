/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.tail.batches.{ Batch, BatchCursor }
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

class IterantDropSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  fixture.test("Iterant[Task].drop equivalence with List.drop") { implicit s =>
    check3 { (list: List[Int], idx: Int, nr: Int) =>
      val iter = arbitraryListToIterant[Task, Int](list, math.abs(idx) + 1, allowErrors = false)
      val stream = iter ++ Iterant[Task].of(1, 2, 3)
      val n = Math.floorMod(nr, 50)
      stream.drop(n).toListL <-> stream.toListL.map(_.drop(n))
    }
  }

  fixture.test("Iterant.drop protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty))
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.drop(Int.MaxValue)
      received <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  fixture.test("Iterant.drop protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty))
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.drop(Int.MaxValue)
      received <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  fixture.test("Iterant.drop preserves resource safety") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval]
      .nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int]))
      .guarantee(stop)
    val stream = source.drop(1)
    stream.completedL.value()
    assertEquals(effect, 1)
  }

  fixture.test("NextBatch.drop preserves referential transparency") { implicit s =>
    var effect = 0
    val batch = Batch.fromIterable(new Iterable[Int] {
      def iterator: Iterator[Int] = {
        effect += 1
        Iterator(1, 2)
      }
    })

    val source = Iterant[Coeval].nextBatchS(batch, Coeval(Iterant[Coeval].empty[Int]))
    assertEquals(effect, 0)
    assertEquals(source.foldLeftL(0)(_ + _).value(), 3)
    assertEquals(effect, 1)
  }

  fixture.test("NextCursor.drop preserves referential transparency") { implicit s =>
    var effect = 0
    val cursor = BatchCursor.fromIterator(new Iterator[Int] {
      val i = Iterator(1, 2)
      def hasNext: Boolean = i.hasNext
      def next(): Int = { effect += 1; i.next() }
    })

    assertEquals(effect, 0)
    val source = Iterant[Coeval].nextCursorS(cursor, Coeval(Iterant[Coeval].empty[Int]))
    assertEquals(effect, 0)

    assertEquals(source.foldLeftL(0)(_ + _).value(), 3)
    assertEquals(effect, 2)
  }
}
