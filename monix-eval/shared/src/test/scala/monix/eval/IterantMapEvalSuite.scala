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

object IterantMapEvalSuite extends BaseTestSuite {
  test("Iterant.mapEval covariant identity") { implicit s =>
    check1 { (list: List[Int]) =>
      val r = Iterant.fromIterable(list).mapEval(x => Task(x)).toListL
      r === Task.now(list)
    }
  }

  test("Iterant.mapEval covariant composition") { implicit s =>
    check3 { (list: List[Int], f: Int => Int, g: Int => Int) =>
      val r1 = Iterant.fromIterable(list)
        .mapEval(x => Task(f(x)))
        .mapEval(x => Task(g(x)))
        .toListL

      val r2 = Iterant.fromIterable(list)
        .mapEval(x => Task(f(x)).flatMap(y => Task(g(y))))
        .toListL

      r1 === r2
    }
  }

  test("Iterant.mapEval equivalence") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = Iterant.fromIterable(list).mapEval(x => Task(f(x))).toListL
      r === Task.now(list.map(f))
    }
  }

  test("Iterant.mapEval equivalence (batched)") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = Iterant.fromIterable(list).mapEval(x => Task(f(x))).toListL
      r === Task.now(list.map(f))
    }
  }

  test("Iterant.mapEval can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant.raiseError[Int](dummy)
    assertEquals(stream, stream.mapEval(x => Task(x)))
  }

  test("Iterant.next.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant.now(1)
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant.nextSeq.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant.fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant.next.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant.now(1)
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant.nextSeq.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant.fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval should protect against indirect user errors") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToIterant(list, idx)
      val received = source.mapEval(_ => Task.raiseError[Int](dummy))
      received === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.mapEval should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToIterant(list, idx)
      val received = source.mapEval[Int](_ => throw dummy)
      received === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.mapEval should protect against broken cursors") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterator(dummy)
      val error = Iterant.nextSeqS(cursor, Task.now(Iterant.empty[Int]), Task.unit)
      val stream = (prefix ++ error).mapEval(x => Task.now(x))
      stream === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.mapEval should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterable(dummy)
      val error = Iterant.nextGenS(cursor, Task.now(Iterant.empty[Int]), Task.unit)
      val stream = (prefix ++ error).mapEval(x => Task.now(x))
      stream === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.mapEval flatMap equivalence") { implicit s =>
    check3 { (stream: Iterant[Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.mapEval(x => Task.eval(f(x)))
      val expected = stream.flatMap(x => Iterant.fromTask(Task.eval(f(x))))
      received === expected
    }
  }

  test("Iterant.mapEval suspends the evaluation for NextGen") { implicit s =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionIterable(dummy)
    val iter = Iterant.nextGenS[Int](items, Task.now(Iterant.empty), Task.unit)
    val state = iter.mapEval(Task.now)

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assert(!items.isTriggered, "!items.isTriggered")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval suspends the evaluation for NextSeq") { implicit s =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionIterator(dummy)
    val iter = Iterant.nextSeqS[Int](items, Task.now(Iterant.empty), Task.unit)
    val state = iter.mapEval(Task.now)

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assert(!items.isTriggered, "!items.isTriggered")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval suspends the evaluation for Next") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant.nextS(1, Task.now(Iterant.empty), Task.unit)
    val state = iter.mapEval { x => (throw dummy): Task[Int] }

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval suspends the evaluation for Last") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant.lastS(1)
    val state = iter.mapEval { x => (throw dummy): Task[Int] }

    assert(state.isInstanceOf[Suspend[Int]])
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval doesn't touch Halt") { implicit s =>
    val dummy = DummyException("dummy")
    val iter1: Iterant[Int] = Iterant.haltS(Some(dummy))
    val state1 = iter1.mapEval(Task.now)
    assertEquals(state1, iter1)

    val iter2: Iterant[Int] = Iterant.haltS(None)
    val state2 = iter2.mapEval { x => (throw dummy) : Task[Int] }
    assertEquals(state2, iter2)
  }
}
