/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import cats.laws._
import cats.laws.discipline._

import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import monix.tail.batches.{Batch, BatchCursor}

import scala.util.Failure

object IterantFoldLeftSuite extends BaseTestSuite {
  test("Iterant[Task].toListL (foldLeftL)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Task].fromIterable(list).toListL
      result <-> Task.now(list)
    }
  }

  test("Iterant[Task].toListL (foldLeftL)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Task].fromIterable(list).toListL
      result <-> Task.now(list)
    }
  }

  test("Iterant[Task].toListL (foldLeftL, async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Task].fromIterable(list)
        .mapEval(x => Task(x)).toListL

      result <-> Task.now(list)
    }
  }

  test("Iterant[Task].foldLeftL ends in error") { implicit s =>
    val b = Iterant[Task]
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val stopT = Task { wasCanceled = true }

    val r = b.nextS(1, Task(b.nextS(2, Task(b.raiseError[Int](dummy)), stopT)), stopT).toListL.runAsync
    assert(!wasCanceled, "wasCanceled should not be true")

    s.tick()
    assertEquals(r.value, Some(Failure(dummy)))
    assert(!wasCanceled, "wasCanceled should not be true")
  }

  test("Iterant[Task].foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = Iterant[Task].nextS(1, Task.now(Iterant[Task].empty[Int]), c)
    val result = stream.foldLeftL[Int](throw dummy)((a,e) => a+e).runAsync
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant[Task].foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = Iterant[Task].nextS(1, Task.now(Iterant[Task].empty[Int]), c)
    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    s.tick()
    check(result <-> Task.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant[Task].foldLeftL (async) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task { wasCanceled = true }
    val stream = Iterant[Task].nextS(1, Task(Iterant[Task].nextCursorS(BatchCursor(2,3), Task.now(Iterant[Task].empty[Int]), c)), c)
      .mapEval(x => Task(x))

    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    check(result <-> Task.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant[Task].foldLeftL should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val result = (prefix.onErrorIgnore ++ error).foldLeftL(0)(_+_)
      result <-> Task.raiseError[Int](dummy)
    }
  }

  test("Iterant[Task].foldLeftL should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val generator = new ThrowExceptionBatch(dummy)
      val error = Iterant[Task].nextBatchS(generator, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val result = (prefix.onErrorIgnore ++ error).foldLeftL(0)(_+_)
      result <-> Task.raiseError[Int](dummy)
    }
  }

  test("Iterant[Coeval].toList (Comonad)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Coeval].fromIterable(list).toListL.value
      result == list
    }
  }

  test("Iterant[Coeval].foldLeft (Comonad)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Coeval].fromIterable(list).foldLeftL(0)(_ + _).value
      result == list.sum
    }
  }

  test("Iterant[Coeval].toListL (foldLeftL, lazy)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Coeval].fromIterable(list)
        .mapEval(x => Coeval(x)).toListL

      result <-> Coeval.now(list)
    }
  }

  test("Iterant[Coeval].foldLeftL ends in error") { implicit s =>
    val b = Iterant[Coeval]
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val r = b.nextS(1, Coeval(b.nextS(2, Coeval(b.raiseError[Int](dummy)), c)), c).toListL.runTry

    assertEquals(r, Failure(dummy))
    assert(!wasCanceled, "wasCanceled should not be true")
  }

  test("Iterant[Coeval].foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty[Int]), c)
    val result = stream.foldLeftL[Int](throw dummy)((a,e) => a+e).runTry
    assertEquals(result, Failure(dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant[Coeval].foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty[Int]), c)
    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    check(result <-> Coeval.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant[Coeval].foldLeftL (batched) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = Iterant[Coeval].nextCursorS(BatchCursor(1,2,3), Coeval.now(Iterant[Coeval].empty[Int]), c)
    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    check(result <-> Coeval.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant[Coeval].foldLeftL (async) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = Iterant[Coeval].nextS(1,
      Coeval(Iterant[Coeval].nextCursorS(BatchCursor(2,3), Coeval.now(Iterant[Coeval].empty[Int]), c)), c)
      .mapEval(x => Coeval(x))

    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    check(result <-> Coeval.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  test("Iterant[Coeval].foldLeftL should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor: BatchCursor[Int] = new ThrowExceptionCursor(dummy)
      val error = Iterant[Coeval].nextCursorS(cursor, Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val result = (prefix.onErrorIgnore ++ error).foldLeftL(0)(_+_)
      result <-> Coeval.raiseError[Int](dummy)
    }
  }

  test("Iterant[Coeval].foldLeftL should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val generator: Batch[Int] = new ThrowExceptionBatch(dummy)
      val error = Iterant[Coeval].nextBatchS(generator, Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val result = (prefix.onErrorIgnore ++ error).foldLeftL(0)(_+_)
      result <-> Coeval.raiseError[Int](dummy)
    }
  }

  test("Iterant[Coeval, Int].foldL is consistent with foldLeftL") { implicit s =>
    check1 { (stream: Iterant[Coeval, Int]) =>
      stream.foldL <-> stream.foldLeftL(0)(_ + _)
    }
  }

  test("Iterant.countL consistent with List.length") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val i = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      i.countL <-> Coeval(list.length)
    }
  }
}
