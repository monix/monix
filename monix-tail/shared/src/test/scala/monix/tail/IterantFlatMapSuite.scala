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
import monix.execution.exceptions.DummyException
import monix.tail.batches.BatchCursor

import scala.util.{ Failure, Success }

object IterantFlatMapSuite extends BaseTestSuite {
  test("Iterant[Task].flatMap equivalence with List.flatMap") { implicit s =>
    check2 { (stream: Iterant[Task, Int], f: Int => List[Long]) =>
      val result = stream.flatMap(x => Iterant[Task].fromList(f(x))).toListL
      val expected = stream.toListL.map((list: List[Int]) => list.flatMap(f))
      result <-> expected
    }
  }

  test("Iterant[Task].flatMap can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].raiseError[Int](dummy)
    assertEquals(stream, stream.flatMap(x => Iterant[Task].of(x)))
  }

  test("Iterant[Task].next.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Task]
      .nextS(1, Task.evalAsync(Iterant[Task].empty[Int]))
      .guarantee(Task.evalAsync { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runToFuture

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Task].nextCursor.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Task]
      .nextCursorS(BatchCursor(1, 2, 3), Task.evalAsync(Iterant[Task].empty[Int]))
      .guarantee(Task.evalAsync { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runToFuture

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Task].next.flatMap chains stop") { implicit s =>
    var effects = Vector.empty[Int]
    val stop1T = Task.eval { effects = effects :+ 1 }
    val stream1: Iterant[Task, Int] =
      Iterant[Task].nextS(1, Task.now(Iterant[Task].haltS[Int](None))).guarantee(stop1T)

    val stop2T = Task.eval { effects = effects :+ 2 }
    val stream2: Iterant[Task, Int] =
      Iterant[Task].nextS(2, Task.now(Iterant[Task].haltS[Int](None))).guarantee(stop2T)

    val stop3T = Task.eval { effects = effects :+ 3 }
    val stream3: Iterant[Task, Int] =
      Iterant[Task].nextS(3, Task.now(Iterant[Task].haltS[Int](None))).guarantee(stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    val result = composed.headOptionL.runToFuture; s.tick()
    assertEquals(result.value, Some(Success(Some(6))))
    assertEquals(effects, Vector(3, 2, 1))
  }

  test("Iterant[Task].nextCursor.flatMap works for large lists") { implicit s =>
    val count = 100000
    val list = (0 until count).toList
    val sumTask = Iterant[Task]
      .fromList(list)
      .flatMap(x => Iterant[Task].fromList(List(x, x, x)))
      .foldLeftL(0L)(_ + _)

    val f = sumTask.runToFuture; s.tick()
    assertEquals(f.value, Some(Success(3 * (count.toLong * (count - 1) / 2))))
  }

  test("Iterant[Task].flatMap should protect against indirect user errors") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToIterant[Task, Int](list, idx)
      val received = source.flatMap(_ => Iterant[Task].raiseError[Int](dummy))
      received <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].flatMap should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToIterant[Task, Int](list, idx)
      val received = source.flatMap[Int](_ => throw dummy)
      received <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].flatMap should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]))
      val stream = (prefix.onErrorIgnore ++ error).flatMap(x => Iterant[Task].now(x))
      stream <-> prefix.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].flatMap should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val generator = new ThrowExceptionBatch(dummy)
      val error = Iterant[Task].nextBatchS(generator, Task.now(Iterant[Task].empty[Int]))
      val stream = (prefix.onErrorIgnore ++ error).flatMap(x => Iterant[Task].now(x))
      stream <-> prefix.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].flatMap equivalence with List.flatMap") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], f: Int => List[Long]) =>
      val result = stream.flatMap(x => Iterant[Coeval].fromList(f(x))).toListL
      val expected = stream.toListL.map((list: List[Int]) => list.flatMap(f))
      result <-> expected
    }
  }

  test("Iterant[Coeval].flatMap can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Coeval].raiseError[Int](dummy)
    assertEquals(stream, stream.flatMap(x => Iterant[Coeval].pure(x)))
  }

  test("Iterant[Coeval].next.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Coeval].nextS(1, Coeval(Iterant[Coeval].empty[Int])).guarantee(Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry()

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Coeval].nextCursor.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Coeval]
      .nextCursorS(BatchCursor(1, 2, 3), Coeval(Iterant[Coeval].empty[Int]))
      .guarantee(Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry()

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Coeval].next.flatMap chains stop") { implicit s =>
    var effects = Vector.empty[Int]
    val stop1T = Coeval.eval { effects = effects :+ 1 }
    val stream1: Iterant[Coeval, Int] =
      Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].haltS[Int](None))).guarantee(stop1T)

    val stop2T = Coeval.eval { effects = effects :+ 2 }
    val stream2: Iterant[Coeval, Int] =
      Iterant[Coeval].nextS(2, Coeval.now(Iterant[Coeval].haltS[Int](None))).guarantee(stop2T)

    val stop3T = Coeval.eval { effects = effects :+ 3 }
    val stream3: Iterant[Coeval, Int] =
      Iterant[Coeval].nextS(3, Coeval.now(Iterant[Coeval].haltS[Int](None))).guarantee(stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    assertEquals(composed.headOptionL.value(), Some(6))
    assertEquals(effects, Vector(3, 2, 1))
  }

  test("Iterant[Coeval].flatMap should protect against indirect user errors") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      val received = source.flatMap(_ => Iterant[Coeval].raiseError[Int](dummy))
      received <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].flatMap should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToIterant[Coeval, Int](list, idx).onErrorIgnore
      val received = source.flatMap[Int](_ => throw dummy)
      received <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].flatMap should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Coeval].nextCursorS(cursor, Coeval.now(Iterant[Coeval].empty[Int]))
      val stream = (prefix.onErrorIgnore ++ error).flatMap(x => Iterant[Coeval].now(x))
      stream <-> prefix.onErrorIgnore ++ Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].flatMap should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionBatch(dummy)
      val error = Iterant[Coeval].nextBatchS(cursor, Coeval.now(Iterant[Coeval].empty[Int]))
      val stream = (prefix ++ error).flatMap(x => Iterant[Coeval].now(x))
      stream <-> prefix ++ Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.unsafeFlatMap <-> flatMap for pure iterants") { implicit s =>
    check2 { (iter: Iterant[Coeval, Int], f: Int => Iterant[Coeval, Int]) =>
      iter.unsafeFlatMap(f) <-> iter.flatMap(f)
    }
  }

  test("Iterant.concatMap is alias of flatMap") { implicit s =>
    check2 { (iter: Iterant[Coeval, Int], f: Int => Iterant[Coeval, Int]) =>
      iter.flatMap(f) <-> iter.concatMap(f)
    }
  }

  test("Iterant.concat is alias of flatten") { implicit s =>
    check2 { (iter: Iterant[Coeval, Int], f: Int => Iterant[Coeval, Int]) =>
      iter.map(f).flatten <-> iter.map(f).concat
    }
  }

  test("fa.map(f).flatten <-> fa.flatMap(f)") { implicit s =>
    check2 { (iter: Iterant[Coeval, Int], f: Int => Iterant[Coeval, Int]) =>
      iter.map(f).flatten <-> iter.flatMap(f)
    }
  }
}
