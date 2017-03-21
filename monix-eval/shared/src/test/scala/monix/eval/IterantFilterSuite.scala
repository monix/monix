/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.eval

import monix.eval.Iterant.Suspend
import monix.execution.exceptions.DummyException
import scala.util.Failure

object IterantFilterSuite extends BaseTestSuite {
  test("Iterant.filter <=> List.filter") { implicit s =>
    check2 { (stream: Iterant[Int], p: Int => Boolean) =>
      val received: Task[List[Int]] = stream.filter(p).toListL
      val expected = stream.toListL.map((l: List[Int]) => l.filter(p))
      received === expected
    }
  }

  test("Iterant.filter protects against user error") { implicit s =>
    check1 { (stream: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val received = (stream ++ Iterant.now(1)).filter(_ => throw dummy)
      received === Iterant.raiseError(dummy)
    }
  }

  test("Iterant.filter flatMap equivalence") { implicit s =>
    check2 { (stream: Iterant[Int], p: Int => Boolean) =>
      val received = stream.filter(p)
      val expected = stream.flatMap(x => if (p(x)) Iterant.now(x) else Iterant.empty[Int])
      received === expected
    }
  }

  test("Iterant.filter suspends the evaluation for NextGen") { implicit s =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionIterable(dummy)
    val iter = Iterant.nextGenS(items, Task.now(Iterant.empty[Int]), Task.unit)
    val state = iter.filter { x => throw dummy }

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assert(!items.isTriggered, "!items.isTriggered")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.filter suspends the evaluation for NextSeq") { implicit s =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionIterator(dummy)
    val iter = Iterant.nextSeqS(items, Task.now(Iterant.empty[Int]), Task.unit)
    val state = iter.filter { x => throw dummy }

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assert(!items.isTriggered, "!items.isTriggered")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.filter suspends the evaluation for Next") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant.nextS(1, Task.now(Iterant.empty[Int]), Task.unit)
    val state = iter.filter { x => (throw dummy) : Boolean }

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.filter suspends the evaluation for Last") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant.lastS(1)
    val state = iter.filter { x => throw dummy }

    assert(state.isInstanceOf[Suspend[Int]])
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.filter doesn't touch Halt") { implicit s =>
    val dummy = DummyException("dummy")
    val iter1: Iterant[Int] = Iterant.haltS[Int](Some(dummy))
    val state1 = iter1.filter { x => true }
    assertEquals(state1, iter1)

    val iter2: Iterant[Int] = Iterant.haltS[Int](None)
    val state2 = iter2.filter { x => (throw dummy) : Boolean }
    assertEquals(state2, iter2)
  }
}
