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

object StreamMapEvalSuite extends BaseTestSuite {
  test("TaskStream.mapEval covariant identity") { implicit s =>
    check1 { (list: List[Int]) =>
      val r = TaskStream.fromIterable(list).mapEval(x => Task(x)).toListL
      r === Task.now(list)
    }
  }

  test("TaskStream.mapEval covariant composition") { implicit s =>
    check3 { (list: List[Int], f: Int => Int, g: Int => Int) =>
      val r1 = TaskStream.fromIterable(list)
        .mapEval(x => Task(f(x)))
        .mapEval(x => Task(g(x)))
        .toListL

      val r2 = TaskStream.fromIterable(list)
        .mapEval(x => Task(f(x)).flatMap(y => Task(g(y))))
        .toListL

      r1 === r2
    }
  }

  test("TaskStream.mapEval equivalence") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = TaskStream.fromIterable(list).mapEval(x => Task(f(x))).toListL
      r === Task.now(list.map(f))
    }
  }

  test("TaskStream.mapEval equivalence (batched)") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = TaskStream.fromIterable(list, batchSize = 4).mapEval(x => Task(f(x))).toListL
      r === Task.now(list.map(f))
    }
  }

  test("TaskStream.mapEval can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.raiseError[Int](dummy)
    assertEquals(stream, stream.mapEval(x => Task(x)))
  }

  test("TaskStream.next.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.next(1, Task(TaskStream.empty))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("TaskStream.nextSeq.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.nextSeq(List(1,2,3), Task(TaskStream.empty))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("TaskStream.nextLazy.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.nextLazy(Task(1), Task(TaskStream.empty))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("TaskStream.next.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.next(1, Task(TaskStream.empty))
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("TaskStream.nextSeq.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.nextSeq(List(1,2,3), Task(TaskStream.empty))
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("TaskStream.nextLazy.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.nextLazy(Task(1), Task(TaskStream.empty))
    val result = stream.mapEval[Int](_ => Task.raiseError(dummy)).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("CoevalStream.mapEval covariant identity") { implicit s =>
    check1 { (list: List[Int]) =>
      val r = CoevalStream.fromIterable(list).mapEval(x => Coeval(x)).toListL
      r === Coeval.now(list)
    }
  }

  test("CoevalStream.mapEval covariant composition") { implicit s =>
    check3 { (list: List[Int], f: Int => Int, g: Int => Int) =>
      val r1 = CoevalStream.fromIterable(list)
        .mapEval(x => Coeval(f(x)))
        .mapEval(x => Coeval(g(x)))
        .toListL

      val r2 = CoevalStream.fromIterable(list)
        .mapEval(x => Coeval(f(x)).flatMap(y => Coeval(g(y))))
        .toListL

      r1 === r2
    }
  }

  test("CoevalStream.mapEval equivalence") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = CoevalStream.fromIterable(list).mapEval(x => Coeval(f(x))).toListL
      r === Coeval.now(list.map(f))
    }
  }

  test("CoevalStream.mapEval equivalence (batched)") { implicit s =>
    check2 { (list: List[Int], f: Int => Int) =>
      val r = CoevalStream.fromIterable(list, batchSize = 4).mapEval(x => Coeval(f(x))).toListL
      r === Coeval.now(list.map(f))
    }
  }

  test("CoevalStream.mapEval can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.raiseError[Int](dummy)
    assertEquals(stream, stream.mapEval(x => Coeval(x)))
  }

  test("CoevalStream.next.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.next(1, Coeval(CoevalStream.empty))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("CoevalStream.nextSeq.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.nextSeq(List(1,2,3), Coeval(CoevalStream.empty))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("CoevalStream.nextLazy.mapEval guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.nextLazy(Coeval(1), Coeval(CoevalStream.empty))
    val result = stream.mapEval[Int](_ => throw dummy).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("CoevalStream.next.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.next(1, Coeval(CoevalStream.empty))
    val result = stream.mapEval[Int](_ => Coeval.raiseError(dummy)).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("CoevalStream.nextSeq.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.nextSeq(List(1,2,3), Coeval(CoevalStream.empty))
    val result = stream.mapEval[Int](_ => Coeval.raiseError(dummy)).toListL.runTry
    assertEquals(result, Failure(dummy))
  }

  test("CoevalStream.nextLazy.mapEval guards against indirect user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.nextLazy(Coeval(1), Coeval(CoevalStream.empty))
    val result = stream.mapEval[Int](_ => Coeval.raiseError(dummy)).toListL.runTry
    assertEquals(result, Failure(dummy))
  }
}
