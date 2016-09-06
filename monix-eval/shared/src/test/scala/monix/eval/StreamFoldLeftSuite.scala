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

object StreamFoldLeftSuite extends BaseTestSuite {
  test("TaskStream.toListL (foldLeftL)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = TaskStream.fromIterable(list).toListL
      result === Task.now(list)
    }
  }

  test("TaskStream.toListL (foldLeftL, batched)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = TaskStream.fromIterable(list, batchSize = 4).toListL
      result === Task.now(list)
    }
  }

  test("TaskStream.toListL (foldLeftL, async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = TaskStream.fromIterable(list)
        .mapEval(x => Task(x)).toListL

      result === Task.now(list)
    }
  }

  test("TaskStream.foldLeftL ends in error") { implicit s =>
    import TaskStream._
    val dummy = DummyException("dummy")
    val c = Task.unit
    val r = cons(1, Task(cons(2, Task(raiseError(dummy)), c)), c).toListL.runAsync
    s.tick()
    assertEquals(r.value, Some(Failure(dummy)))
  }

  test("TaskStream.foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.cons(1, Task.now(TaskStream.empty))
    val result = stream.foldLeftL[Int](throw dummy)((a,e) => a+e).runAsync
    s.tick(); assertEquals(result.value, Some(Failure(dummy)))
  }

  test("TaskStream.foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.cons(1, Task.now(TaskStream.empty))
    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    check(result === Task.raiseError(dummy))
  }

  test("TaskStream.foldLeftL (batched) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream.consSeq(List(1,2,3), Task.now(TaskStream.empty))
    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    check(result === Task.raiseError(dummy))
  }

  test("TaskStream.foldLeftL (async) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = TaskStream(1,2,3).mapEval(x => Task(x))
    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    check(result === Task.raiseError(dummy))
  }

  test("CoevalStream.toListL (foldLeftL)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = CoevalStream.fromIterable(list).toListL
      result === Coeval.now(list)
    }
  }

  test("CoevalStream.toListL (foldLeftL, batched)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = CoevalStream.fromIterable(list, batchSize = 4).toListL
      result === Coeval.now(list)
    }
  }

  test("CoevalStream.toListL (foldLeftL, lazy)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = CoevalStream.fromIterable(list)
        .mapEval(x => Coeval(x)).toListL

      result === Coeval.now(list)
    }
  }

  test("CoevalStream.foldLeftL ends in error") { implicit s =>
    import CoevalStream._
    val dummy = DummyException("dummy")
    val c = Coeval.unit
    val r = cons(1, Coeval(cons(2, Coeval(raiseError(dummy)), c)), c).toListL.runTry
    s.tick()
    assertEquals(r, Failure(dummy))
  }

  test("CoevalStream.foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.cons(1, Coeval.now(CoevalStream.empty))
    val result = stream.foldLeftL[Int](throw dummy)((a,e) => a+e).runTry
    assertEquals(result, Failure(dummy))
  }

  test("CoevalStream.foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = CoevalStream.cons(1, Coeval.now(CoevalStream.empty))
    val result = stream.foldLeftL(0)((a,e) => throw dummy).runTry
    assertEquals(result, Failure(dummy))
  }
}
