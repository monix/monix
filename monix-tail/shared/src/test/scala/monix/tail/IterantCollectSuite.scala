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
import scala.util.Failure

object IterantCollectSuite extends BaseTestSuite {
  test("Iterant.collect <=> List.collect") { implicit s =>
    check3 { (stream: Iterant[Task, Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.collect(pf).toListL
      val expected = stream.toListL.map(_.collect(pf))
      received <-> expected
    }
  }

  test("Iterant.collect protects against user error") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val received = (stream.onErrorIgnore ++ Iterant[Task].now(1)).collect[Int] { case _ => throw dummy }
      received <-> Iterant[Task].raiseError(dummy)
    }
  }

  test("Iterant.collect flatMap equivalence") { implicit s =>
    check3 { (stream: Iterant[Task, Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.collect(pf)
      val expected = stream.flatMap(x => if (pf.isDefinedAt(x)) Iterant[Task].now(pf(x)) else Iterant[Task].empty[Int])
      received <-> expected
    }
  }

  test("Iterant.collect suspends the evaluation for NextBatch") { _ =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionBatch(dummy)
    val iter = Iterant[Coeval].nextBatchS(items, Coeval.now(Iterant.empty[Coeval, Int]), Coeval.unit)
    val state = iter.collect { case _ => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Coeval,Int]], "state.isInstanceOf[Suspend[Coeval,Int]]")
    assert(!items.isTriggered, "!batch.isTriggered")
    assertEquals(state.toListL.runTry, Failure(dummy))
  }

  test("Iterant.collect suspends the evaluation for NextCursor") { _ =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionCursor(dummy)
    val iter = Iterant[Coeval].nextCursorS(items, Coeval.now(Iterant.empty[Coeval, Int]), Coeval.unit)
    val state = iter.collect { case _ => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Coeval,Int]], "state.isInstanceOf[Suspend[Coeval,Int]]")
    assert(!items.isTriggered, "!batch.isTriggered")
    assertEquals(state.toListL.runTry, Failure(dummy))
  }

  test("Iterant.collect suspends the evaluation for Next") { _ =>
    var effect: Int = 0
    val iter = Iterant[Coeval].nextS(1, Coeval.now(Iterant.empty[Coeval, Int]), Coeval.unit)
    val state = iter.collect { case _ => effect += 1; 1 }

    assertEquals(effect, 0)
    assertEquals(state.toListL.value, List(1))
    assertEquals(effect, 1)
  }

  test("Iterant.collect suspends the evaluation for Last") { _ =>
    var effect = 0
    val iter = Iterant[Coeval].lastS(1)
    val state = iter.collect { case _ => effect += 1; 1 }

    assertEquals(effect, 0)
    assertEquals(state.toListL.value, List(1))
    assertEquals(effect, 1)
  }

  test("Iterant.collect doesn't touch Halt") { _ =>
    val dummy = DummyException("dummy")
    val iter1: Iterant[Coeval, Int] = Iterant[Coeval].haltS(Some(dummy))
    val state1 = iter1.collect { case x => x }
    assertEquals(state1, iter1)

    val iter2: Iterant[Coeval, Int] = Iterant[Coeval].haltS(None)
    val state2 = iter2.collect { case _ => (throw dummy) : Int }
    assertEquals(state2, iter2)
  }

  test("Iterant.collect preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1,2,3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source.collect { case x => x }
    stream.earlyStop.value
    assertEquals(effect, 1)
  }
}
