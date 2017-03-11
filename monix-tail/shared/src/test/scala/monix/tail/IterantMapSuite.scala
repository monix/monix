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

import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import scala.util.Failure

object IterantMapSuite extends BaseTestSuite {
  test("Iterant[Task].map equivalence with List.map") { implicit s =>
    check2 { (stream: Iterant[Task, Int], f: Int => Long) =>
      stream.map(f).toListL ===
        stream.toListL.map((list: List[Int]) => list.map(f))
    }
  }

  test("Iterant[Task].map can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].raiseError[Int](dummy)
    assertEquals(stream, stream.map(identity))
  }

  test("Iterant[Task].next.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Task].nextS(1, Task(Iterant[Task].empty), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Task].nextSeq.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Task].nextSeqS(List(1,2,3).iterator, Task(Iterant[Task].empty), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Task].map should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterantTask(list, idx)
      val received = (iterant ++ Iterant[Task].now(1)).map[Int](_ => throw dummy)
      received === Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].map should protect against broken cursors") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterator(dummy)
      val error = Iterant[Task].nextSeqS(cursor, Task.now(Iterant[Task].empty), Task.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].map should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterable(dummy)
      val error = Iterant[Task].nextGenS(cursor, Task.now(Iterant[Task].empty), Task.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].map equivalence with List.map") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], f: Int => Long) =>
      stream.map(f).toListL ===
        stream.toListL.map((list: List[Int]) => list.map(f))
    }
  }

  test("Iterant[Coeval].map can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Coeval].raiseError[Int](dummy)
    assertEquals(stream, stream.map(identity))
  }

  test("Iterant[Coeval].next.map guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Coeval].nextS(1, Coeval(Iterant[Coeval].empty), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Coeval].nextSeq.map guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Coeval].nextSeqS(List(1,2,3).iterator, Coeval(Iterant[Coeval].empty), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Coeval].map should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterantCoeval(list, idx)
      val received = (iterant ++ Iterant[Coeval].now(1)).map[Int](_ => throw dummy)
      received === Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].map should protect against broken cursors") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterator(dummy)
      val error = Iterant[Coeval].nextSeqS(cursor, Coeval.now(Iterant[Coeval].empty), Coeval.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].map should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterable(dummy)
      val error = Iterant[Coeval].nextGenS(cursor, Coeval.now(Iterant[Coeval].empty), Coeval.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }
}
