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

object IterantMapSuite extends BaseTestSuite {
  test("Iterant[Task].map equivalence with List.map") { implicit s =>
    check2 { (stream: Iterant[Task, Int], f: Int => Long) =>
      stream.map(f).toListL <->
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

    val stream = Iterant[Task].nextS(1, Task(Iterant[Task].empty[Int]), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Task].nextCursor.map guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Task].nextCursorS(BatchCursor(1,2,3), Task(Iterant[Task].empty[Int]), Task { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Task].map should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      var effect = 0

      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant[Task, Int](list, idx)
      val received = (iterant ++ Iterant[Task].of(1, 2))
        .doOnEarlyStop(Task.eval { effect += 1 })
        .map[Int](_ => throw dummy)
        .completeL.map(_ => 0)
        .onErrorRecover { case _: DummyException => effect }

      received <-> Task.pure(1)
    }
  }

  test("Iterant[Task].map should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val stream = (prefix.onErrorIgnore ++ error).map(x => x)
      stream <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].map should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionBatch(dummy)
      val error = Iterant[Task].nextBatchS(cursor, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val stream = (prefix.onErrorIgnore ++ error).map(x => x)
      stream <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].map equivalence with List.map") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], f: Int => Long) =>
      stream.map(f).toListL <->
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

    val stream = Iterant[Coeval].nextS(1, Coeval(Iterant[Coeval].empty[Int]), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Coeval].nextCursor.map guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Coeval].nextCursorS(BatchCursor(1,2,3), Coeval(Iterant[Coeval].empty[Int]), Coeval { isCanceled = true })
    val result = stream.map[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Coeval].map should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant[Coeval, Int](list, idx)
      val received = (iterant ++ Iterant[Coeval].now(1)).map[Int](_ => throw dummy)
      received <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].map should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor: BatchCursor[Int] = new ThrowExceptionCursor(dummy)
      val error = Iterant[Coeval].nextCursorS(cursor, Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val stream = (prefix ++ error).map(x => x)
      stream <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].map should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor: Batch[Int] = new ThrowExceptionBatch(dummy)
      val error = Iterant[Coeval].nextBatchS(cursor, Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val stream = (prefix ++ error).map(x => x)
      stream <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.map preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1,2,3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source.map(x => x)
    stream.earlyStop.value
    assertEquals(effect, 1)
  }

  test("Iterant.foreach") { implicit s =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      val fa = Coeval.suspend {
        var sum = 0
        iter.foreach { e => sum += e }.map(_ => sum)
      }

      fa <-> iter.foldLeftL(0)(_ + _)
    }
  }
}
