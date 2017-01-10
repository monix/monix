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
import monix.tail.Iterant.{Halt, Last, Next, NextSeq, Suspend}

import scala.util.{Failure, Success}

object IterantFlatMapSuite extends BaseTestSuite {
  test("AsyncStream.flatMap equivalence with List.flatMap") { implicit s =>
    check2 { (stream: AsyncStream[Int], f: Int => List[Long]) =>
      val result = stream.flatMap(x => AsyncStream.fromList(f(x))).toListL
      val expected = stream.toListL.map((list: List[Int]) => list.flatMap(f))
      result === expected
    }
  }

  test("AsyncStream.flatMap can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = AsyncStream.raiseError[Int](dummy)
    assertEquals(stream, stream.flatMap(x => AsyncStream(x)))
  }

  test("AsyncStream.next.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = AsyncStream.nextS(1, Task(AsyncStream.empty), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("AsyncStream.nextSeq.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = AsyncStream.nextSeqS(Cursor.fromSeq(List(1,2,3)), Task(AsyncStream.empty), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("AsyncStream.next.flatMap chains stop") { implicit s =>
    var effects = Vector.empty[Int]
    val stop1T = Task.eval { effects = effects :+ 1 }
    val stream1: AsyncStream[Int] =
      AsyncStream.nextS(1, Task.now(AsyncStream.haltS[Int](None)), stop1T)

    val stop2T = Task.eval { effects = effects :+ 2 }
    val stream2: AsyncStream[Int] =
      AsyncStream.nextS(2, Task.now(AsyncStream.haltS[Int](None)), stop2T)

    val stop3T = Task.eval { effects = effects :+ 3 }
    val stream3: AsyncStream[Int] =
      AsyncStream.nextS(3, Task.now(AsyncStream.haltS[Int](None)), stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    composed match {
      case Iterant.Next(head, _, stop) =>
        assertEquals(head, 6)
        assertEquals(stop.runSyncMaybe, Right(()))
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }

  test("AsyncStream.nextSeq.flatMap chains stop") { implicit s =>
    def firstNext[A](streamable: Iterant[Task,A]): Task[Iterant[Task,A]] =
      streamable match {
        case Suspend(rest, _) =>
          rest.flatMap(firstNext)
        case Next(_,_,_) | NextSeq(_,_,_) | Halt(_) | Last(_) =>
          Task.now(streamable)
      }

    var effects = Vector.empty[Int]
    val stop1T = Task.eval { effects = effects :+ 1 }
    val stream1: AsyncStream[Int] =
      AsyncStream.fromList(List(1)).doOnStop(stop1T)

    val stop2T = Task.eval { effects = effects :+ 2 }
    val stream2: AsyncStream[Int] =
      AsyncStream.fromList(List(2)).doOnStop(stop2T)

    val stop3T = Task.eval { effects = effects :+ 3 }
    val stream3: AsyncStream[Int] =
      AsyncStream.fromList(List(3)).doOnStop(stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    firstNext(composed).runSyncMaybe match {
      case Right(Iterant.NextSeq(head, _, stop)) =>
        assertEquals(head.toList, List(6))
        assertEquals(stop.runSyncMaybe, Right(()))
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }

  test("AsyncStream.nextSeq.flatMap works for large lists") { implicit s =>
    val count = 100000
    val list = (0 until count).toList
    val sumTask = AsyncStream.fromList(list)
      .flatMap(x => AsyncStream.fromList(List(x,x,x)))
      .foldLeftL(0L)(_+_)

    val f = sumTask.runAsync; s.tick()
    assertEquals(f.value, Some(Success(3 * (count.toLong * (count - 1) / 2))))
  }

  test("LazyStream.flatMap equivalence with List.flatMap") { implicit s =>
    check2 { (stream: LazyStream[Int], f: Int => List[Long]) =>
      val result = stream.flatMap(x => LazyStream.fromList(f(x))).toListL
      val expected = stream.toListL.map((list: List[Int]) => list.flatMap(f))
      result === expected
    }
  }

  test("LazyStream.flatMap can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = LazyStream.raiseError[Int](dummy)
    assertEquals(stream, stream.flatMap(x => LazyStream.pure(x)))
  }

  test("LazyStream.next.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = LazyStream.nextS(1, Coeval(LazyStream.empty), Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("LazyStream.nextSeq.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = LazyStream.nextSeqS(Cursor.fromSeq(List(1,2,3)), Coeval(LazyStream.empty), Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("LazyStream.next.flatMap chains stop") { implicit s =>
    var effects = Vector.empty[Int]
    val stop1T = Coeval.eval { effects = effects :+ 1 }
    val stream1: LazyStream[Int] =
      LazyStream.nextS(1, Coeval.now(LazyStream.haltS[Int](None)), stop1T)

    val stop2T = Coeval.eval { effects = effects :+ 2 }
    val stream2: LazyStream[Int] =
      LazyStream.nextS(2, Coeval.now(LazyStream.haltS[Int](None)), stop2T)

    val stop3T = Coeval.eval { effects = effects :+ 3 }
    val stream3: LazyStream[Int] =
      LazyStream.nextS(3, Coeval.now(LazyStream.haltS[Int](None)), stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    composed match {
      case Iterant.Next(head, _, stop) =>
        assertEquals(head, 6)
        assertEquals(stop.value, ())
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }

  test("LazyStream.nextSeq.flatMap chains stop") { implicit s =>
    def firstNext[A](streamable: Iterant[Coeval,A]): Coeval[Iterant[Coeval,A]] =
      streamable match {
        case Suspend(rest, _) =>
          rest.flatMap(firstNext)
        case Next(_,_,_) | NextSeq(_,_,_) | Halt(_) | Last(_) =>
          Coeval.now(streamable)
      }

    var effects = Vector.empty[Int]
    val stop1T = Coeval.eval { effects = effects :+ 1 }
    val stream1: LazyStream[Int] =
      LazyStream.fromList(List(1)).doOnStop(stop1T)

    val stop2T = Coeval.eval { effects = effects :+ 2 }
    val stream2: LazyStream[Int] =
      LazyStream.fromList(List(2)).doOnStop(stop2T)

    val stop3T = Coeval.eval { effects = effects :+ 3 }
    val stream3: LazyStream[Int] =
      LazyStream.fromList(List(3)).doOnStop(stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    firstNext(composed).value match {
      case Iterant.NextSeq(head, _, stop) =>
        assertEquals(head.toList, List(6))
        assertEquals(stop.value, ())
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }
}
