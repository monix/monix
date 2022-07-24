/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.eval.{ Coeval, Task }
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.tail.batches.{ Batch, BatchCursor }
import scala.util.Failure

class IterantFoldLeftSuite extends BaseTestSuite {
  fixture.test("Iterant[Task].toListL (foldLeftL)") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val result = arbitraryListToIterant[Task, Int](list, idx, allowErrors = false).toListL
      result <-> Task.now(list)
    }
  }

  fixture.test("Iterant[Task].toListL (foldLeftL, async)") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val result = arbitraryListToIterant[Task, Int](list, idx, allowErrors = false)
        .mapEval(x => Task.evalAsync(x))
        .toListL

      result <-> Task.now(list)
    }
  }

  fixture.test("Iterant[Task].foldLeftL ends in error") { implicit s =>
    val b = Iterant[Task]
    val dummy = DummyException("dummy")
    var effect = 0
    val stopT = Task.evalAsync { effect += 1 }

    val r = b
      .nextS(1, Task.evalAsync(b.nextS(2, Task.evalAsync(b.raiseError[Int](dummy))).guarantee(stopT)))
      .guarantee(stopT)
      .toListL
      .runToFuture
    assertEquals(effect, 0)

    s.tick()
    assertEquals(r.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  fixture.test("Iterant[Task].foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task.evalAsync { wasCanceled = true }
    val stream = Iterant[Task].nextS(1, Task.now(Iterant[Task].empty[Int])).guarantee(c)
    val result = stream.foldLeftL[Int](throw dummy)((a, e) => a + e).runToFuture
    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(!wasCanceled, "wasCanceled should be false")
  }

  fixture.test("Iterant[Task].foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Task.evalAsync { wasCanceled = true }
    val stream = Iterant[Task].nextS(1, Task.now(Iterant[Task].empty[Int])).guarantee(c)
    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    s.tick()
    check(result <-> Task.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  fixture.test("Iterant[Task].foldLeftL (async) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0
    val c = Task.evalAsync { effect += 1 }
    val stream = Iterant[Task]
      .nextS(
        1,
        Task.evalAsync(Iterant[Task].nextCursorS(BatchCursor(2, 3), Task.now(Iterant[Task].empty[Int])).guarantee(c))
      )
      .guarantee(c)
      .mapEval(x => Task.evalAsync(x))

    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    check(result <-> Task.raiseError[Int](dummy))
    assertEquals(effect, 1)
  }

  fixture.test("Iterant[Task].foldLeftL should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]))
      val result = (prefix.onErrorIgnore ++ error).foldLeftL(0)(_ + _)
      result <-> Task.raiseError[Int](dummy)
    }
  }

  fixture.test("Iterant[Task].foldLeftL should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val generator = new ThrowExceptionBatch(dummy)
      val error = Iterant[Task].nextBatchS(generator, Task.now(Iterant[Task].empty[Int]))
      val result = (prefix.onErrorIgnore ++ error).foldLeftL(0)(_ + _)
      result <-> Task.raiseError[Int](dummy)
    }
  }

  fixture.test("Iterant[Coeval].toList (Comonad)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Coeval].fromIterable(list).toListL.value()
      result == list
    }
  }

  fixture.test("Iterant[Coeval].foldLeft (Comonad)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Coeval].fromIterable(list).foldLeftL(0)(_ + _).value()
      result == list.sum
    }
  }

  fixture.test("Iterant[Coeval].toListL (foldLeftL, lazy)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = Iterant[Coeval]
        .fromIterable(list)
        .mapEval(x => Coeval(x))
        .toListL

      result <-> Coeval.now(list)
    }
  }

  fixture.test("Iterant[Coeval].foldLeftL ends in error") { implicit s =>
    val b = Iterant[Coeval]
    val dummy = DummyException("dummy")
    var effect = 0
    val c = Coeval { effect += 1 }
    val r = b.nextS(1, Coeval(b.nextS(2, Coeval(b.raiseError[Int](dummy))).guarantee(c))).guarantee(c).toListL.runTry()

    assertEquals(r, Failure(dummy))
    assertEquals(effect, 2)
  }

  fixture.test("Iterant[Coeval].foldLeftL protects against user code in the seed") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty[Int])).guarantee(c)
    val result = stream.foldLeftL[Int](throw dummy)((a, e) => a + e).runTry()
    assertEquals(result, Failure(dummy))
    assert(!wasCanceled, "wasCanceled should be false")
  }

  fixture.test("Iterant[Coeval].foldLeftL protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty[Int])).guarantee(c)
    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    check(result <-> Coeval.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  fixture.test("Iterant[Coeval].foldLeftL (batched) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCanceled = false
    val c = Coeval { wasCanceled = true }
    val stream = Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int])).guarantee(c)
    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    check(result <-> Coeval.raiseError[Int](dummy))
    assert(wasCanceled, "wasCanceled should be true")
  }

  fixture.test("Iterant[Coeval].foldLeftL (async) protects against user code in function f") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0
    val c = Coeval { effect += 1 }
    val stream = Iterant[Coeval]
      .nextS(
        1,
        Coeval(Iterant[Coeval].nextCursorS(BatchCursor(2, 3), Coeval.now(Iterant[Coeval].empty[Int])).guarantee(c))
      )
      .guarantee(c)
      .mapEval(x => Coeval(x))

    val result = stream.foldLeftL(0)((_, _) => throw dummy)
    check(result <-> Coeval.raiseError[Int](dummy))
    assertEquals(effect, 1)
  }

  fixture.test("Iterant[Coeval].foldLeftL should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor: BatchCursor[Int] = new ThrowExceptionCursor(dummy)
      val error = Iterant[Coeval].nextCursorS(cursor, Coeval.now(Iterant[Coeval].empty[Int]))
      val result = (prefix.onErrorIgnore ++ error).foldLeftL(0)(_ + _)
      result <-> Coeval.raiseError[Int](dummy)
    }
  }

  fixture.test("Iterant[Coeval].foldLeftL should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val generator: Batch[Int] = new ThrowExceptionBatch(dummy)
      val error = Iterant[Coeval].nextBatchS(generator, Coeval.now(Iterant[Coeval].empty[Int]))
      val result = (prefix.onErrorIgnore ++ error).foldLeftL(0)(_ + _)
      result <-> Coeval.raiseError[Int](dummy)
    }
  }

  fixture.test("Iterant[Coeval, Int].foldL is consistent with foldLeftL") { implicit s =>
    check1 { (stream: Iterant[Coeval, Int]) =>
      stream.foldL <-> stream.foldLeftL(0)(_ + _)
    }
  }

  fixture.test("Iterant[Coeval, Int].sumL is consistent with foldLeftL") { implicit s =>
    check1 { (stream: Iterant[Coeval, Int]) =>
      stream.sumL <-> stream.foldLeftL(0)(_ + _)
    }
  }

  fixture.test("Iterant.countL consistent with List.length") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val i = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      i.countL <-> Coeval(list.length.toLong)
    }
  }

  fixture.test("earlyStop gets called for failing `rest` on Next node") { implicit s =>
    var effect = 0

    def stop(i: Int): Coeval[Unit] = Coeval { effect += i }
    val dummy = DummyException("dummy")
    val node3 = Iterant[Coeval].nextS(3, Coeval.raiseError(dummy)).guarantee(stop(3))
    val node2 = Iterant[Coeval].nextS(2, Coeval(node3)).guarantee(stop(2))
    val node1 = Iterant[Coeval].nextS(1, Coeval(node2)).guarantee(stop(1))

    assertEquals(node1.toListL.runTry(), Failure(dummy))
    assertEquals(effect, 6)
  }

  fixture.test("earlyStop doesn't get called for Last node") { implicit s =>
    var effect = 0

    def stop(i: Int): Coeval[Unit] = Coeval { effect += i }
    val dummy = DummyException("dummy")
    val node3 = Iterant[Coeval].lastS(3)
    val node2 = Iterant[Coeval].nextS(2, Coeval(node3)).guarantee(stop(2))
    val node1 = Iterant[Coeval].nextS(1, Coeval(node2)).guarantee(stop(1))

    assertEquals(node1.foldLeftL(0)((_, el) => if (el == 3) throw dummy else el).runTry(), Failure(dummy))
    assertEquals(effect, 3)
  }

  fixture.test("foldLeftL handles Scope's release before the rest of the stream") { implicit s =>
    val triggered = Atomic(false)
    val fail = DummyException("fail")

    val lh = Iterant[Coeval].scopeS[Unit, Int](
      Coeval.unit,
      _ => Coeval(Iterant.pure(1)),
      (_, _) => Coeval(triggered.set(true))
    )

    val stream = Iterant[Coeval].concatS(
      Coeval(lh),
      Coeval {
        if (!triggered.getAndSet(true))
          Iterant[Coeval].raiseError[Int](fail)
        else
          Iterant[Coeval].empty[Int]
      }
    )

    assertEquals(stream.toListL.value(), List(1))
  }

  fixture.test("foldLeftL handles Scope's release after use is finished") { implicit s =>
    val triggered = Atomic(false)
    val fail = DummyException("fail")

    val stream = Iterant[Coeval].scopeS[Unit, Int](
      Coeval.unit,
      _ =>
        Coeval(1 +: Iterant[Coeval].suspend {
          if (triggered.getAndSet(true))
            Iterant[Coeval].raiseError[Int](fail)
          else
            Iterant[Coeval].empty[Int]
        }),
      (_, _) => {
        Coeval(triggered.set(true))
      }
    )

    assertEquals((0 +: stream :+ 2).toListL.value(), List(0, 1, 2))
  }
}
