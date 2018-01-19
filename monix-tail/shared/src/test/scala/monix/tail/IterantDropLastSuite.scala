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
import monix.tail.batches.BatchCursor

object IterantDropLastSuite extends BaseTestSuite {
  test("Iterant.dropLast is equivalent with List.dropRight") { implicit s =>

    check3 { (list: List[Int], idx: Int, nr: Int) =>
      val stream = arbitraryListToIterant[Task, Int](list, math.abs(idx) + 1).onErrorIgnore
      val n = if (nr == 0) 0 else math.abs(math.abs(nr) % 20)

      stream.dropLast(n).toListL <-> stream.toListL.map(_.dropRight(n))
    }
  }
  test("Iterant.dropLast protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.dropLast(10)
      received <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.dropLast protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.dropLast(10)
      received <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.dropLast preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source.dropLast(3)
    stream.earlyStop.value
    assertEquals(effect, 1)
  }

  test("Iterant.dropLast triggers early stop on exception") { _ =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      val cancelable = BooleanCancelable()
      val dummy = DummyException("dummy")
      val suffix = Iterant[Coeval].nextCursorS[Int](new ThrowExceptionCursor(dummy), Coeval.now(Iterant[Coeval].empty), Coeval.unit)
      val stream = (iter.onErrorIgnore ++ suffix).doOnEarlyStop(Coeval.eval(cancelable.cancel()))

      intercept[DummyException] {
        stream.dropLast(1).toListL.value
      }
      cancelable.isCanceled
    }
  }

  test("Iterant.dropLast works for infinite cursors") { implicit s =>
    check2 { (el: Int, _: Int) =>
      val stream = Iterant[Coeval].nextCursorS(BatchCursor.continually(el), Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val received = stream.dropLast(1).take(1).toListL
      val expected = Coeval(Stream.continually(el).dropRight(1).take(1).toList)

      received <-> expected
    }
  }

  test("Iterant.dropLast suspends side effects") { implicit s =>
    check1 { stream: Iterant[Task, Int] =>
      stream.dropLast(1) <-> stream.dropLast(1)
    }
  }
}