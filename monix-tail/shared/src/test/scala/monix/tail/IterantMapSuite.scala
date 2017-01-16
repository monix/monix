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

object IterantMapSuite extends BaseTestSuite {
  test("AsyncStream.map equivalence with List.map") { implicit s =>
    check2 { (stream: AsyncStream[Int], f: Int => Long) =>
      stream.map(f).toListL ===
        stream.toListL.map((list: List[Int]) => list.map(f))
    }
  }

  test("AsyncStream.map can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = AsyncStream.raiseError[Int](dummy)
    assertEquals(stream, stream.map(identity))
  }

  test("AsyncStream.next.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = AsyncStream.nextS(1, Task(AsyncStream.empty), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("AsyncStream.nextSeq.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = AsyncStream.nextSeqS(Cursor.fromSeq(List(1,2,3)), Task(AsyncStream.empty), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("AsyncStream.map should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToAsyncStream(list, idx)
      val received = (iterant ++ AsyncStream.now(1)).map[Int](_ => throw dummy)
      received === AsyncStream.haltS[Int](Some(dummy))
    }
  }

  test("AsyncStream.map should protect against broken cursors") { implicit s =>
    check1 { (prefix: AsyncStream[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = AsyncStream.nextSeqS(cursor, Task.now(AsyncStream.empty), Task.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === AsyncStream.haltS[Int](Some(dummy))
    }
  }

  test("AsyncStream.map should protect against broken generators") { implicit s =>
    check1 { (prefix: AsyncStream[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionGenerator(dummy)
      val error = AsyncStream.nextGenS(cursor, Task.now(AsyncStream.empty), Task.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === AsyncStream.haltS[Int](Some(dummy))
    }
  }

  test("LazyStream.map equivalence with List.map") { implicit s =>
    check2 { (stream: LazyStream[Int], f: Int => Long) =>
      stream.map(f).toListL ===
        stream.toListL.map((list: List[Int]) => list.map(f))
    }
  }

  test("LazyStream.map can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = LazyStream.raiseError[Int](dummy)
    assertEquals(stream, stream.map(identity))
  }

  test("LazyStream.next.map guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = LazyStream.nextS(1, Coeval(LazyStream.empty), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("LazyStream.nextSeq.map guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = LazyStream.nextSeqS(Cursor.fromSeq(List(1,2,3)), Coeval(LazyStream.empty), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("LazyStream.map should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToLazyStream(list, idx)
      val received = (iterant ++ LazyStream.now(1)).map[Int](_ => throw dummy)
      received === LazyStream.haltS[Int](Some(dummy))
    }
  }

  test("LazyStream.map should protect against broken cursors") { implicit s =>
    check1 { (prefix: LazyStream[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = LazyStream.nextSeqS(cursor, Coeval.now(LazyStream.empty), Coeval.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === LazyStream.haltS[Int](Some(dummy))
    }
  }

  test("LazyStream.map should protect against broken generators") { implicit s =>
    check1 { (prefix: LazyStream[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionGenerator(dummy)
      val error = LazyStream.nextGenS(cursor, Coeval.now(LazyStream.empty), Coeval.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === LazyStream.haltS[Int](Some(dummy))
    }
  }
}
