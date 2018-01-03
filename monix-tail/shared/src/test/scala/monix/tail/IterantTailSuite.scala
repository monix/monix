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
import monix.execution.exceptions.DummyException
import monix.tail.Iterant.Suspend
import monix.tail.batches.BatchCursor

object IterantTailSuite extends BaseTestSuite {
  test("Iterant.tail is equivalent with List.tail") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val iter = arbitraryListToIterant[Task, Int](list, math.abs(idx))
      val stream = iter ++ Iterant[Task].fromList(List(1,2,3))
      stream.tail.toListL <-> stream.toListL.map(_.tail)
    }
  }

  test("Iterant.tail protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.tail
      received <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.tail protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.tail
      received <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.tail suspends execution for NextCursor") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant[Coeval].nextCursorS[Int](
      new ThrowExceptionCursor(dummy),
      Coeval.now(Iterant[Coeval].empty[Int]),
      Coeval.unit)
      .tail

    assert(iter.isInstanceOf[Suspend[Coeval, Int]], "iter.isInstanceOf[Suspend[Coeval, Int]]")
    intercept[DummyException](iter.toListL.value)
  }

  test("Iterant.tail suspends execution for NextBatch") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant[Coeval].nextBatchS[Int](
      new ThrowExceptionBatch(dummy),
      Coeval.now(Iterant[Coeval].empty[Int]),
      Coeval.unit)
      .tail

    assert(iter.isInstanceOf[Suspend[Coeval, Int]], "iter.isInstanceOf[Suspend[Coeval, Int]]")
    intercept[DummyException](iter.toListL.value)
  }

  test("Iterant.tail preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1,2,3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source.tail
    stream.earlyStop.value
    assertEquals(effect, 1)
  }
}
