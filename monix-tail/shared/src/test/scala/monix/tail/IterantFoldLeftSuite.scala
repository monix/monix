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

object IterantFoldLeftSuite extends BaseTestSuite {
  test("AsyncStream.toListL (foldLeftL)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = AsyncStream.fromIterable(list).toListL
      result === Task.now(list)
    }
  }

  test("AsyncStream.toListL (foldLeftL)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = AsyncStream.fromIterable(list).toListL
      result === Task.now(list)
    }
  }

  test("AsyncStream.toListL (foldLeftL, async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = AsyncStream.fromIterable(list)
        .mapEval(x => Task(x)).toListL

      result === Task.now(list)
    }
  }

  test("AsyncStream.foldLeftL ends in error") { implicit s =>
    import AsyncStream._
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val r = nextS(1, Task(nextS(2, Task(raiseError(dummy)), c)), c).toListL.runAsync
    s.tick()
    assertEquals(r.value, Some(Failure(dummy)))
    assert(!wasCanceled, "wasCanceled should not be true")
  }

  test("AsyncStream.foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = AsyncStream.nextS(1, Task.now(AsyncStream.empty), c)
    val result = stream.foldLeftL[Int](throw dummy)((a,e) => a+e).runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("AsyncStream.foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = AsyncStream.nextS(1, Task.now(AsyncStream.empty), c)
    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    s.tick()
    check(result === Task.raiseError(dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("AsyncStream.foldLeftL (async) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = AsyncStream.nextS(1, Task(AsyncStream.nextSeqS(Cursor.fromSeq(List(2,3)), Task.now(AsyncStream.empty), c)), c)
      .mapEval(x => Task(x))

    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    check(result === Task.raiseError(dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("LazyStream.toListL (foldLeftL)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = LazyStream.fromIterable(list).toListL
      result === Coeval.now(list)
    }
  }

  test("LazyStream.toListL (foldLeftL, lazy)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = LazyStream.fromIterable(list)
        .mapEval(x => Coeval(x)).toListL

      result === Coeval.now(list)
    }
  }

  test("LazyStream.foldLeftL ends in error") { implicit s =>
    import LazyStream._
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val r = nextS(1, Coeval(nextS(2, Coeval(raiseError(dummy)), c)), c).toListL.runTry
    assertEquals(r, Failure(dummy))
    assert(!wasCanceled, "wasCanceled should not be true")
  }

  test("LazyStream.foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = LazyStream.nextS(1, Coeval.now(LazyStream.empty), c)
    val result = stream.foldLeftL[Int](throw dummy)((a,e) => a+e).runTry
    assertEquals(result, Failure(dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("LazyStream.foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = LazyStream.nextS(1, Coeval.now(LazyStream.empty), c)
    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    check(result === Coeval.raiseError(dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("LazyStream.foldLeftL (batched) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = LazyStream.nextSeqS(Cursor.fromSeq(List(1,2,3)), Coeval.now(LazyStream.empty), c)
    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    check(result === Coeval.raiseError(dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("LazyStream.foldLeftL (async) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = LazyStream.nextS(1, Coeval(LazyStream.nextSeqS(Cursor.fromSeq(List(2,3)), Coeval.now(LazyStream.empty), c)), c)
      .mapEval(x => Coeval(x))

    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    check(result === Coeval.raiseError(dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }
}
