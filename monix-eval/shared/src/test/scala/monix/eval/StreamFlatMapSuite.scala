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

object StreamFlatMapSuite extends BaseTestSuite {
  test("TaskStream.flatMap equivalence with List.flatMap") { implicit s =>
    check2 { (stream: TaskStream[Int], f: Int => List[Long]) =>
      val result = stream.flatMap(x => TaskStream.fromList(f(x))).toListL
      val expected = stream.toListL.map((list: List[Int]) => list.flatMap(f))
      result === expected
    }
  }

  test("TaskStream.flatMap can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.raiseError[Int](dummy)
    assertEquals(stream, stream.flatMap(x => TaskStream(x)))
  }

  test("TaskStream.cons.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = TaskStream.cons(1, Task(TaskStream.empty), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("TaskStream.consSeq.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = TaskStream.consSeq(List(1,2,3), Task(TaskStream.empty), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("TaskStream.consLazy.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = TaskStream.consLazy(Task(1), Task(TaskStream.empty), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("CoevalStream.flatMap equivalence with List.flatMap") { implicit s =>
    check2 { (stream: CoevalStream[Int], f: Int => List[Long]) =>
      val result = stream.flatMap(x => CoevalStream.fromList(f(x))).toListL
      val expected = stream.toListL.map((list: List[Int]) => list.flatMap(f))
      result === expected
    }
  }

  test("CoevalStream.flatMap can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.raiseError[Int](dummy)
    assertEquals(stream, stream.flatMap(CoevalStream.pure))
  }

  test("CoevalStream.cons.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = CoevalStream.cons(1, Coeval(CoevalStream.empty), Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("CoevalStream.consSeq.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = CoevalStream.consSeq(List(1,2,3), Coeval(CoevalStream.empty), Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("CoevalStream.consLazy.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = CoevalStream.consLazy(Coeval(1), Coeval(CoevalStream.empty), Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }
}
