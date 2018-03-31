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
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.DummyException
import monix.tail.batches.{Batch, BatchCursor}

import scala.util.Failure

object IterantMapBatchSuite extends BaseTestSuite {
  test("Iterant[Task].mapBatch(f) equivalence with List.flatMap(f andThen (_.toList))") { implicit s =>
    check2 { (stream: Iterant[Task, Array[Int]], f: Array[Int] => Long) =>
      val g = f andThen (Batch.apply(_))
      stream.mapBatch(g).toListL <->
        stream.toListL.map(_.flatMap(g andThen (_.toList)))
    }
  }

  test("Iterant[Task].mapBatch works for functions producing batches bigger than recommendedBatchSize") { implicit s =>
    check2 { (list: List[Int], elem: Int) =>
      val stream = Iterant[Task].nextBatchS(Batch.fromSeq(list, batches.defaultBatchSize), Task.delay(Iterant[Task].lastS[Int](elem)), Task.unit)
      val f: Int => List[Int] = List.fill(batches.defaultBatchSize * 2)(_)

      val received = stream.mapBatch(f andThen (Batch.fromSeq(_))).toListL
      val expected = stream.toListL.map(_.flatMap(f))

      received <-> expected
    }
  }

  test("Iterant[Task].mapBatch can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].raiseError[Int](dummy)
    assertEquals(stream, stream.mapBatch(Batch.apply(_)))
  }

  test("Iterant[Task].next.mapBatch guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Task].nextS(1, Task(Iterant[Task].empty[Int]), Task {
      isCanceled = true
    })
    val result = stream.mapBatch[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Task].nextCursor.mapBatch guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Task].nextCursorS(BatchCursor(1, 2, 3), Task(Iterant[Task].empty[Int]), Task {
      isCanceled = true
    })
    val result = stream.mapBatch[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Task].mapBatch should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      var effect = 0

      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant[Task, Int](list, idx)
      val received = (iterant ++ Iterant[Task].of(1, 2))
        .doOnEarlyStop(Task.eval {
          effect += 1
        })
        .mapBatch[Int](_ => throw dummy)
        .completeL.map(_ => 0)
        .onErrorRecover { case _: DummyException => effect }

      received <-> Task.pure(1)
    }
  }

  test("Iterant[Task].mapBatch should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val stream = (prefix.onErrorIgnore ++ error).mapBatch(Batch.apply(_))
      stream <-> prefix.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].mapBatch protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.mapBatch(Batch.apply(_))
      received <-> iter.onErrorIgnore.mapBatch(Batch.apply(_)) ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].mapBatch should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionBatch(dummy)
      val error = Iterant[Task].nextBatchS(cursor, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val stream = (prefix.onErrorIgnore ++ error).mapBatch(Batch.apply(_))
      stream <-> prefix.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].mapBatch suspends side effects") { implicit s =>
    check1 { stream: Iterant[Task, Int] =>
      stream.mapBatch(Batch.apply(_)) <-> stream.mapBatch(Batch.apply(_))
    }
  }

  test("Iterant[Coeval].mapBatch works for infinite cursors") { implicit s =>
    check1 { (el: Int) =>
      val stream = Iterant[Coeval].nextCursorS(BatchCursor.continually(el), Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val received = stream.mapBatch(Batch.apply(_)).take(10).toListL
      val expected = Coeval(Stream.continually(el).take(10).toList)

      received <-> expected
    }
  }

  test("Iterant[Coeval].mapBatch triggers early stop on exception") { _ =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      val cancelable = BooleanCancelable()
      val dummy = DummyException("dummy")
      val suffix = Iterant[Coeval].nextCursorS[Int](new ThrowExceptionCursor(dummy), Coeval.now(Iterant[Coeval].empty), Coeval.unit)
      val stream = (iter.onErrorIgnore ++ suffix).doOnEarlyStop(Coeval.eval(cancelable.cancel()))

      intercept[DummyException] {
        stream.mapBatch(Batch.apply(_)).toListL.value
      }
      cancelable.isCanceled
    }
  }

  test("Iterant[Coeval].mapBatch can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Coeval].raiseError[Int](dummy)
    assertEquals(stream, stream.mapBatch(Batch.apply(_)))
  }

  test("Iterant[Coeval].next.mapBatch guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Coeval].nextS(1, Coeval(Iterant[Coeval].empty[Int]), Coeval {
      isCanceled = true
    })
    val result = stream.mapBatch[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Coeval].nextCursor.mapBatch guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval(Iterant[Coeval].empty[Int]), Coeval {
      isCanceled = true
    })
    val result = stream.mapBatch[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant[Coeval].mapBatch should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val iterant = arbitraryListToIterant[Coeval, Int](list, idx)
      val received = (iterant ++ Iterant[Coeval].now(1)).mapBatch[Int](_ => throw dummy)
      received <-> Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].mapBatch should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor: BatchCursor[Int] = new ThrowExceptionCursor(dummy)
      val error = Iterant[Coeval].nextCursorS(cursor, Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val stream = (prefix ++ error).mapBatch(Batch.apply(_))
      stream <-> prefix ++ Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].mapBatch should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val cursor: Batch[Int] = new ThrowExceptionBatch(dummy)
      val error = Iterant[Coeval].nextBatchS(cursor, Coeval.now(Iterant[Coeval].empty[Int]), Coeval.unit)
      val stream = (prefix ++ error).mapBatch(Batch.apply(_))
      stream <-> prefix ++ Iterant[Coeval].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Coeval].mapBatch preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source.mapBatch(Batch.apply(_))
    stream.earlyStop.value
    assertEquals(effect, 1)
  }
}