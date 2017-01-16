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

package monix.tail

import monix.eval.{Coeval, DummyException, Task}
import scala.util.Failure

object IterantMapEvalSuite extends BaseTestSuite {
  test("AsyncStream.mapEval covariant identity") { implicit s =>
    check1 { (list: List[Int]) =>
      val r = AsyncStream.fromIterable(list).mapEval(x => Task(x)).toListL
      r === Task.now(list)
    }
  }

  test("AsyncStream.mapEval covariant composition") { implicit s =>
    check3 { (list: List[Int], f: Int => Int, g: Int => Int) =>
      val r1 = AsyncStream.fromIterable(list)
        .mapEval(x => Task(f(x)))
        .mapEval(x => Task(g(x)))
        .toListL

      val r2 = AsyncStream.fromIterable(list)
        .mapEval(x => Task(f(x)).flatMap(y => Task(g(y))))
        .toListL

      r1 === r2
    }
  }

  test("AsyncStream.mapEval equivalence") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = AsyncStream.fromIterable(list).mapEval(x => Task(f(x))).toListL
      r === Task.now(list.map(f))
    }
  }

  test("AsyncStream.mapEval equivalence (batched)") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = AsyncStream.fromIterable(list).mapEval(x => Task(f(x))).toListL
      r === Task.now(list.map(f))
    }
  }

  test("AsyncStream.mapEval can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = AsyncStream.raiseError[Int](dummy)
    assertEquals(stream, stream.mapEval(x => Task(x)))
  }

  test("AsyncStream.next.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = AsyncStream.now(1)
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("AsyncStream.nextSeq.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = AsyncStream.fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("AsyncStream.next.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = AsyncStream.now(1)
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("AsyncStream.nextSeq.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = AsyncStream.fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("AsyncStream.mapEval should protect against indirect user errors") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToAsyncStream(list, idx)
      val received = source.mapEval(_ => Task.raiseError[Int](dummy))
      received === AsyncStream.haltS[Int](Some(dummy))
    }
  }

  test("AsyncStream.mapEval should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToAsyncStream(list, idx)
      val received = source.mapEval[Int](_ => throw dummy)
      received === AsyncStream.haltS[Int](Some(dummy))
    }
  }

  test("AsyncStream.mapEval should protect against broken cursors") { implicit s =>
    check1 { (prefix: AsyncStream[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = AsyncStream.nextSeqS(cursor, Task.now(AsyncStream.empty), Task.unit)
      val stream = (prefix ++ error).mapEval(x => Task.now(x))
      stream === AsyncStream.haltS[Int](Some(dummy))
    }
  }

  test("AsyncStream.mapEval should protect against broken generators") { implicit s =>
    check1 { (prefix: AsyncStream[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionGenerator(dummy)
      val error = AsyncStream.nextGenS(cursor, Task.now(AsyncStream.empty), Task.unit)
      val stream = (prefix ++ error).mapEval(x => Task.now(x))
      stream === AsyncStream.haltS[Int](Some(dummy))
    }
  }

  test("LazyStream.mapEval covariant identity") { implicit s =>
    check1 { (list: List[Int]) =>
      val r = LazyStream.fromIterable(list).mapEval(x => Coeval(x)).toListL
      r === Coeval.now(list)
    }
  }

  test("LazyStream.mapEval covariant composition") { implicit s =>
    check3 { (list: List[Int], f: Int => Int, g: Int => Int) =>
      val r1 = LazyStream.fromIterable(list)
        .mapEval(x => Coeval(f(x)))
        .mapEval(x => Coeval(g(x)))
        .toListL

      val r2 = LazyStream.fromIterable(list)
        .mapEval(x => Coeval(f(x)).flatMap(y => Coeval(g(y))))
        .toListL

      r1 === r2
    }
  }

  test("LazyStream.mapEval equivalence") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = LazyStream.fromIterable(list).mapEval(x => Coeval(f(x))).toListL
      r === Coeval.now(list.map(f))
    }
  }

  test("LazyStream.mapEval can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = LazyStream.raiseError[Int](dummy)
    assertEquals(stream, stream.mapEval(x => Coeval(x)))
  }

  test("LazyStream.next.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = LazyStream.now(1)
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("LazyStream.nextSeq.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = LazyStream.fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("LazyStream.next.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = LazyStream.now(1)
    val result = stream.mapEval[Int](_ => Coeval.raiseError(dummy)).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("LazyStream.nextSeq.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = LazyStream.fromList(List(1,2,3))
    val result = stream.mapEval[Int](_ => Coeval.raiseError(dummy)).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("LazyStream.mapEval should protect against indirect user errors") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToLazyStream(list, idx)
      val received = (iterant ++ LazyStream.now(1))
        .mapEval[Int](_ => Coeval.raiseError(dummy))
      received === LazyStream.haltS[Int](Some(dummy))
    }
  }

  test("LazyStream.mapEval should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToLazyStream(list, idx)
      val received = (iterant ++ LazyStream.now(1)).mapEval[Int](_ => throw dummy)
      received === LazyStream.haltS[Int](Some(dummy))
    }
  }

  test("LazyStream.mapEval should protect against broken cursors") { implicit s =>
    check1 { (prefix: LazyStream[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = LazyStream.nextSeqS(cursor, Coeval.now(LazyStream.empty), Coeval.unit)
      val stream = (prefix ++ error).mapEval(x => Coeval.now(x))
      stream === LazyStream.haltS[Int](Some(dummy))
    }
  }

  test("LazyStream.mapEval should protect against broken generators") { implicit s =>
    check1 { (prefix: LazyStream[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionGenerator(dummy)
      val error = LazyStream.nextGenS(cursor, Coeval.now(LazyStream.empty), Coeval.unit)
      val stream = (prefix ++ error).mapEval(x => Coeval.now(x))
      stream === LazyStream.haltS[Int](Some(dummy))
    }
  }
}
