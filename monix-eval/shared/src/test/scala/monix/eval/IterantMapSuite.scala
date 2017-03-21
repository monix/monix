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

import monix.execution.exceptions.DummyException
import scala.util.Failure

object IterantMapSuite extends BaseTestSuite {
  test("Iterant.map equivalence with List.map") { implicit s =>
    check2 { (stream: Iterant[Int], f: Int => Long) =>
      stream.map(f).toListL ===
        stream.toListL.map((list: List[Int]) => list.map(f))
    }
  }

  test("Iterant.map can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant.raiseError[Int](dummy)
    assertEquals(stream, stream.map(identity))
  }

  test("Iterant.next.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant.nextS(1, Task(Iterant.empty[Int]), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant.nextSeq.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant.nextSeqS(List(1,2,3).iterator, Task(Iterant.empty[Int]), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant.map should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant(list, idx)
      val received = (iterant ++ Iterant.now(1)).map[Int](_ => throw dummy)
      received === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.map should protect against broken cursors") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterator(dummy)
      val error = Iterant.nextSeqS(cursor, Task.now(Iterant.empty[Int]), Task.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.map should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterable(dummy)
      val error = Iterant.nextGenS(cursor, Task.now(Iterant.empty[Int]), Task.unit)
      val stream = (prefix ++ error).map(x => x)
      stream === Iterant.haltS[Int](Some(dummy))
    }
  }
}