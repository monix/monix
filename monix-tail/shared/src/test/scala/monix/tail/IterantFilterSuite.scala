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
import monix.tail.Iterant.Suspend
import monix.tail.batches.BatchCursor

import scala.util.Failure

class IterantFilterSuite extends BaseTestSuite {
  fixture.test("Iterant.filter <=> List.filter") { implicit s =>
    check2 { (stream: Iterant[Task, Int], p: Int => Boolean) =>
      val received = stream.filter(p).toListL
      val expected = stream.toListL.map(_.filter(p))
      received <-> expected
    }
  }

  fixture.test("Iterant.filter protects against user error") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val received = (stream.onErrorIgnore ++ Iterant[Task].now(1)).filter(_ => throw dummy)
      received <-> Iterant[Task].raiseError(dummy)
    }
  }

  fixture.test("Iterant.filter flatMap equivalence") { implicit s =>
    check2 { (stream: Iterant[Task, Int], p: Int => Boolean) =>
      val received = stream.filter(p)
      val expected = stream.flatMap(x => if (p(x)) Iterant[Task].now(x) else Iterant[Task].empty[Int])
      received <-> expected
    }
  }

  test("Iterant.filter suspends the evaluation for NextBatch") {
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionBatch(dummy)
    val iter = Iterant[Coeval].nextBatchS(items, Coeval.now(Iterant[Coeval].empty[Int]))
    val state = iter.filter { _ =>
      throw dummy
    }

    assert(state.isInstanceOf[Suspend[Coeval, Int]], "state.isInstanceOf[Suspend[Coeval,Int]]")
    assert(!items.isTriggered, "!batch.isTriggered")
    assertEquals(state.toListL.runTry(), Failure(dummy))
  }

  test("Iterant.filter suspends the evaluation for NextCursor") {
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionCursor(dummy)
    val iter = Iterant[Coeval].nextCursorS(items, Coeval.now(Iterant[Coeval].empty[Int]))
    val state = iter.filter { _ =>
      throw dummy
    }

    assert(state.isInstanceOf[Suspend[Coeval, Int]], "state.isInstanceOf[Suspend[Coeval,Int]]")
    assert(!items.isTriggered, "!batch.isTriggered")
    assertEquals(state.toListL.runTry(), Failure(dummy))
  }

  test("Iterant.filter protects against user code for Next") {
    val dummy = DummyException("dummy")
    val iter = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty[Int]))
    val state = iter.filter { _ =>
      (throw dummy): Boolean
    }
    assertEquals(state.toListL.runTry(), Failure(dummy))
  }

  test("Iterant.filter protects against user code for Last") {
    val dummy = DummyException("dummy")
    val iter = Iterant[Coeval].lastS(1)
    val state = iter.filter { _ =>
      throw dummy
    }
    assertEquals(state.toListL.runTry(), Failure(dummy))
  }

  test("Iterant.filter doesn't touch Halt") {
    val dummy = DummyException("dummy")
    val iter1: Iterant[Coeval, Int] = Iterant[Coeval].haltS[Int](Some(dummy))
    val state1 = iter1.filter { _ =>
      true
    }
    assertEquals(state1, iter1)

    val iter2: Iterant[Coeval, Int] = Iterant[Coeval].haltS[Int](None)
    val state2 = iter2.filter { _ =>
      (throw dummy): Boolean
    }
    assertEquals(state2, iter2)
  }

  fixture.test("Iterant.filter preserves resource safety") { implicit s =>
    var effect = 0
    val source = Iterant[Coeval]
      .nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int]))
      .guarantee(Coeval.eval(effect += 1))
    val stream = source.filter(_ => true)
    stream.completedL.value()
    assertEquals(effect, 1)
  }

  fixture.test("Iterant.withFilter applies filtering in for-comprehension") { implicit s =>
    val source = Iterant[Coeval]
      .nextCursorS(BatchCursor(1, 2, 3, 4, 5), Coeval.now(Iterant[Coeval].empty[Int]))

    val evenValues = (for {
      value <- source if value % 2 == 0
    } yield value).toListL.value()

    assertEquals(evenValues, List(2, 4))
  }
}
