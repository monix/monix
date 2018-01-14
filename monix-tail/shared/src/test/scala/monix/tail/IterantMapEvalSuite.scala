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
import monix.tail.batches.{Batch, BatchCursor}
import scala.util.Failure

object IterantMapEvalSuite extends BaseTestSuite {
  test("Iterant[Task].mapEval covariant identity") { implicit s =>
    check1 { (list: List[Int]) =>
      val r = Iterant[Task].fromIterable(list).mapEval(x => Task(x)).toListL
      r <-> Task.now(list)
    }
  }

  test("Iterant[Task].mapEval covariant composition") { implicit s =>
    check3 { (list: List[Int], f: Int => Int, g: Int => Int) =>
      val r1 = Iterant[Task].fromIterable(list)
        .mapEval(x => Task(f(x)))
        .mapEval(x => Task(g(x)))
        .toListL

      val r2 = Iterant[Task].fromIterable(list)
        .mapEval(x => Task(f(x)).flatMap(y => Task(g(y))))
        .toListL

      r1 <-> r2
    }
  }

  test("Iterant[Task].mapEval equivalence") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = Iterant[Task].fromIterable(list).mapEval(x => Task(f(x))).toListL
      r <-> Task.now(list.map(f))
    }
  }

  test("Iterant[Task].mapEval equivalence (batched)") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = Iterant[Task].fromIterable(list).mapEval(x => Task(f(x))).toListL
      r <-> Task.now(list.map(f))
    }
  }

  test("Iterant[Task].mapEval can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].raiseError[Int](dummy)
    assertEquals(stream, stream.mapEval(x => Task(x)))
  }

  test("Iterant[Task].next.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].now(1)
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant[Task].nextCursor.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant[Task].next.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].now(1)
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant[Task].nextCursor.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("Iterant[Task].mapEval should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      var effect = 0

      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant[Task, Int](list, idx)
      val received = (iterant ++ Iterant[Task].of(1, 2))
        .doOnEarlyStop(Task.eval { effect += 1 })
        .mapEval[Int](_ => throw dummy)
        .completeL.map(_ => 0)
        .onErrorRecover { case _: DummyException => effect }

      received <-> Task.pure(1)
    }
  }

  test("Iterant[Task].mapEval should protect against indirect errors") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      var effect = 0

      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant[Task, Int](list, idx)
      val received = (iterant ++ Iterant[Task].of(1, 2))
        .doOnEarlyStop(Task.eval { effect += 1 })
        .mapEval[Int](_ => Task.raiseError(dummy))
        .completeL.map(_ => 0)
        .onErrorRecover { case _: DummyException => effect }

      received <-> Task.pure(1)
    }
  }

  test("Iterant[Task].mapEval should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val stream = (prefix.onErrorIgnore ++ error).mapEval(x => Task.now(x))
      stream <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].mapEval should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionBatch(dummy)
      val error = Iterant[Task].nextBatchS(cursor, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val stream = (prefix.onErrorIgnore ++ error).mapEval(x => Task.now(x))
      stream <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].mapEval covariant identity") { implicit s =>
    check1 { (list: List[Int]) =>
      val r = Iterant[Coeval].fromIterable(list).mapEval(x => Coeval(x)).toListL
      r <-> Coeval.now(list)
    }
  }

  test("Iterant[Coeval].mapEval covariant composition") { implicit s =>
    check3 { (list: List[Int], f: Int => Int, g: Int => Int) =>
      val r1 = Iterant[Coeval].fromIterable(list)
        .mapEval(x => Coeval(f(x)))
        .mapEval(x => Coeval(g(x)))
        .toListL

      val r2 = Iterant[Coeval].fromIterable(list)
        .mapEval(x => Coeval(f(x)).flatMap(y => Coeval(g(y))))
        .toListL

      r1 <-> r2
    }
  }

  test("Iterant[Coeval].mapEval equivalence") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = Iterant[Coeval].fromIterable(list).mapEval(x => Coeval(f(x))).toListL
      r <-> Coeval.now(list.map(f))
    }
  }

  test("Iterant[Coeval].mapEval can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Coeval].raiseError[Int](dummy)
    assertEquals(stream, stream.mapEval(x => Coeval(x)))
  }

  test("Iterant[Coeval].next.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Coeval].now(1)
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("Iterant[Coeval].nextCursor.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Coeval].fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("Iterant[Coeval].next.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Coeval].now(1)
    val result = stream.mapEval[Int](_ => Coeval.raiseError(dummy)).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("Iterant[Coeval].nextCursor.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Coeval].fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => Coeval.raiseError(dummy)).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("Iterant[Coeval].mapEval should protect against indirect user errors") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant[Coeval, Int](list, idx)
      val received = (iterant ++ Iterant[Coeval].now(1))
        .mapEval[Int](_ => Coeval.raiseError(dummy))
      received <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].mapEval should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant[Coeval, Int](list, idx)
      val received = (iterant ++ Iterant[Coeval].now(1)).mapEval[Int](_ => throw dummy)
      received <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].mapEval should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor: BatchCursor[Int] = new ThrowExceptionCursor(dummy)
      val error = Iterant[Coeval].nextCursorS(cursor, Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val stream = (prefix ++ error).mapEval(x => Coeval.now(x))
      stream <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].mapEval should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor: Batch[Int] = new ThrowExceptionBatch(dummy)
      val error = Iterant[Coeval].nextBatchS(cursor, Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val stream = (prefix ++ error).mapEval(x => Coeval.now(x))
      stream <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.mapEval suspends the evaluation for NextBatch") { implicit s =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionBatch(dummy)
    val iter = Iterant[Task].nextBatchS[Int](items, Task.now(Iterant[Task].empty), Task.unit)
    val state = iter.mapEval(Task.now)

    assert(state.isInstanceOf[Suspend[Task, Int]], "state.isInstanceOf[Suspend[Task, Int]]")
    assert(!items.isTriggered, "!batch.isTriggered")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval suspends the evaluation for NextCursor") { implicit s =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionCursor(dummy)
    val iter = Iterant[Task].nextCursorS[Int](items, Task.now(Iterant[Task].empty), Task.unit)
    val state = iter.mapEval(Task.now)

    assert(state.isInstanceOf[Suspend[Task, Int]], "state.isInstanceOf[Suspend[Task, Int]]")
    assert(!items.isTriggered, "!batch.isTriggered")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval suspends the evaluation for Next") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant[Task].nextS(1, Task.now(Iterant[Task].empty[Int]), Task.unit)
    val state = iter.mapEval { _ => (throw dummy): Task[Int] }

    assert(state.isInstanceOf[Suspend[Task, Int]], "state.isInstanceOf[Suspend[Int]]")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval suspends the evaluation for Last") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant[Task].lastS(1)
    val state = iter.mapEval { _ => (throw dummy): Task[Int] }

    assert(state.isInstanceOf[Suspend[Task, Int]])
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.mapEval doesn't touch Halt") { implicit s =>
    val dummy = DummyException("dummy")
    val iter1: Iterant[Task, Int] = Iterant[Task].haltS(Some(dummy))
    val state1 = iter1.mapEval(Task.now)
    assertEquals(state1, iter1)

    val iter2: Iterant[Task, Int] = Iterant[Task].haltS(None)
    val state2 = iter2.mapEval { _ => (throw dummy) : Task[Int] }
    assertEquals(state2, iter2)
  }

  test("Iterant.mapEval preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1,2,3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source.mapEval(x => Coeval.now(x))
    stream.earlyStop.value
    assertEquals(effect, 1)
  }
}
