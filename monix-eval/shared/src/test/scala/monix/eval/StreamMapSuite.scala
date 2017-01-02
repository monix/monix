/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

import scala.util.Failure

object StreamMapSuite extends BaseTestSuite {
  test("TaskStream.map equivalence with List.map") { implicit s =>
    check2 { (stream: TaskStream[Int], f: Int => Long) =>
      stream.map(f).toListL ===
        stream.toListL.map((list: List[Int]) => list.map(f))
    }
  }

  test("TaskStream.map can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.raiseError[Int](dummy)
    assertEquals(stream, stream.map(identity))
  }

  test("TaskStream.next.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = TaskStream.nextS(1, Task(TaskStream.empty), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("TaskStream.nextSeq.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = TaskStream.nextSeqS(List(1,2,3), Task(TaskStream.empty), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("TaskStream.nextLazy.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = TaskStream.nextLazyS(Task(1), Task(TaskStream.empty), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("CoevalStream.map equivalence with List.map") { implicit s =>
    check2 { (stream: CoevalStream[Int], f: Int => Long) =>
      stream.map(f).toListL ===
        stream.toListL.map((list: List[Int]) => list.map(f))
    }
  }

  test("CoevalStream.map can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.raiseError[Int](dummy)
    assertEquals(stream, stream.map(identity))
  }

  test("CoevalStream.next.map guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = CoevalStream.nextS(1, Coeval(CoevalStream.empty), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("CoevalStream.nextSeq.map guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = CoevalStream.nextSeqS(List(1,2,3), Coeval(CoevalStream.empty), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("CoevalStream.nextLazy.map guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = CoevalStream.nextLazyS(Coeval(1), Coeval(CoevalStream.empty), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }
}
