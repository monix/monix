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

object IterantFoldLeftSuite extends BaseTestSuite {
  test("Iterant.toListL (foldLeftL)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromIterable(list).toListL
      result === Task.now(list)
    }
  }

  test("Iterant.toListL (foldLeftL, async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant.fromIterable(list)
        .mapEval(x => Task(x)).toListL

      result === Task.now(list)
    }
  }

  test("Iterant.foldLeftL ends in error") { implicit s =>
    val b = Iterant
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val stopT = Task { wasCanceled = true }

    val r = b.nextS(1, Task(b.nextS(2, Task(b.raiseError[Int](dummy)), stopT)), stopT).toListL.runAsync
    assert(!wasCanceled, "wasCanceled should not be true")

    s.tick()
    assertEquals(r.value, Some(Failure(dummy)))
    assert(!wasCanceled, "wasCanceled should be false")
  }

  test("Iterant.foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = Iterant.nextS(1, Task.now(Iterant.empty[Int]), c)
    val result = stream.foldLeftL[Int](throw dummy)((a,e) => a+e).runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant.foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = Iterant.nextS(1, Task.now(Iterant.empty[Int]), c)
    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    s.tick()
    check(result === Task.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant.foldLeftL (async) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = Iterant.nextS(1, Task(Iterant.nextSeqS(List(2,3).iterator, Task.now(Iterant.empty[Int]), c)), c)
      .mapEval(x => Task(x))

    val result = stream.foldLeftL(0)((a,e) => throw dummy)
    check(result === Task.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }


  test("Iterant.foldLeftL should protect against broken cursors") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterator(dummy)
      val error = Iterant.nextSeqS(cursor, Task.now(Iterant.empty[Int]), Task.unit)
      val result = (prefix ++ error).foldLeftL(0)(_+_)
      result === Task.raiseError[Int](dummy)
    }
  }

  test("Iterant.foldLeftL should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val generator = new ThrowExceptionIterable(dummy)
      val error = Iterant.nextGenS(generator, Task.now(Iterant.empty[Int]), Task.unit)
      val result = (prefix ++ error).foldLeftL(0)(_+_)
      result === Task.raiseError[Int](dummy)
    }
  }
}
