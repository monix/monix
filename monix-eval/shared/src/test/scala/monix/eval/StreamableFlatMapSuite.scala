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

import monix.eval.Streamable.{Halt, Next, NextSeq, Suspend}
import scala.util.{Failure, Success}

object StreamableFlatMapSuite extends BaseTestSuite {
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

  test("TaskStream.next.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = TaskStream.nextS(1, Task(TaskStream.empty), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("TaskStream.nextSeq.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = TaskStream.nextSeqS(List(1,2,3), Task(TaskStream.empty), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("TaskStream.next.flatMap chains stop") { implicit s =>
    var effects = Vector.empty[Int]
    val stop1T = Task.eval { effects = effects :+ 1 }
    val stream1: TaskStream[Int] =
      TaskStream.nextS(1, Task.now(TaskStream.halt[Int](None)), stop1T)

    val stop2T = Task.eval { effects = effects :+ 2 }
    val stream2: TaskStream[Int] =
      TaskStream.nextS(2, Task.now(TaskStream.halt[Int](None)), stop2T)

    val stop3T = Task.eval { effects = effects :+ 3 }
    val stream3: TaskStream[Int] =
      TaskStream.nextS(3, Task.now(TaskStream.halt[Int](None)), stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    composed.stream match {
      case Streamable.Next(head, _, stop) =>
        assertEquals(head, 6)
        assertEquals(stop.runSyncMaybe, Right(()))
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }

  test("TaskStream.nextSeq.flatMap chains stop") { implicit s =>
    def firstNext[A](streamable: Streamable[Task,A]): Task[Streamable[Task,A]] =
      streamable match {
        case Suspend(rest, _) =>
          rest.flatMap(firstNext)
        case Next(_,_,_) | NextSeq(_,_,_) | Halt(_) =>
          Task.now(streamable)
      }

    var effects = Vector.empty[Int]
    val stop1T = Task.eval { effects = effects :+ 1 }
    val stream1: TaskStream[Int] =
      TaskStream.nextSeqS(List(1), Task.now(TaskStream.halt[Int](None)), stop1T)

    val stop2T = Task.eval { effects = effects :+ 2 }
    val stream2: TaskStream[Int] =
      TaskStream.nextSeqS(List(2), Task.now(TaskStream.halt[Int](None)), stop2T)

    val stop3T = Task.eval { effects = effects :+ 3 }
    val stream3: TaskStream[Int] =
      TaskStream.nextSeqS(List(3), Task.now(TaskStream.halt[Int](None)), stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    firstNext(composed.stream).runSyncMaybe match {
      case Right(Streamable.NextSeq(head, _, stop)) =>
        assertEquals(head, List(6))
        assertEquals(stop.runSyncMaybe, Right(()))
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }

  test("TaskStream.nextSeq.flatMap works for large lists") { implicit s =>
    val count = 100000
    val list = (0 until count).toList
    val sumTask = TaskStream.nextSeq(list, Task.now(TaskStream.empty))
      .flatMap(x => TaskStream.fromSeq(List(x,x,x)))
      .foldLeftL(0L)(_+_)

    val f = sumTask.runAsync; s.tick()
    assertEquals(f.value, Some(Success(3L * (count * (count - 1) / 2))))
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

  test("CoevalStream.next.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = CoevalStream.nextS(1, Coeval(CoevalStream.empty), Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("CoevalStream.nextSeq.flatMap guards against direct user code errors") { _ =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = CoevalStream.nextSeqS(List(1,2,3), Coeval(CoevalStream.empty), Coeval { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runTry

    assertEquals(result, Failure(dummy))
    assert(isCanceled, "isCanceled should be true")
  }

  test("CoevalStream.next.flatMap chains stop") { implicit s =>
    var effects = Vector.empty[Int]
    val stop1T = Coeval.eval { effects = effects :+ 1 }
    val stream1: CoevalStream[Int] =
      CoevalStream.nextS(1, Coeval.now(CoevalStream.halt[Int](None)), stop1T)

    val stop2T = Coeval.eval { effects = effects :+ 2 }
    val stream2: CoevalStream[Int] =
      CoevalStream.nextS(2, Coeval.now(CoevalStream.halt[Int](None)), stop2T)

    val stop3T = Coeval.eval { effects = effects :+ 3 }
    val stream3: CoevalStream[Int] =
      CoevalStream.nextS(3, Coeval.now(CoevalStream.halt[Int](None)), stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    composed.stream match {
      case Streamable.Next(head, _, stop) =>
        assertEquals(head, 6)
        assertEquals(stop.value, ())
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }

  test("CoevalStream.nextSeq.flatMap chains stop") { implicit s =>
    def firstNext[A](streamable: Streamable[Coeval,A]): Coeval[Streamable[Coeval,A]] =
      streamable match {
        case Suspend(rest, _) =>
          rest.flatMap(firstNext)
        case Next(_,_,_) | NextSeq(_,_,_) | Halt(_) =>
          Coeval.now(streamable)
      }

    var effects = Vector.empty[Int]
    val stop1T = Coeval.eval { effects = effects :+ 1 }
    val stream1: CoevalStream[Int] =
      CoevalStream.nextSeqS(List(1), Coeval.now(CoevalStream.halt[Int](None)), stop1T)

    val stop2T = Coeval.eval { effects = effects :+ 2 }
    val stream2: CoevalStream[Int] =
      CoevalStream.nextSeqS(List(2), Coeval.now(CoevalStream.halt[Int](None)), stop2T)

    val stop3T = Coeval.eval { effects = effects :+ 3 }
    val stream3: CoevalStream[Int] =
      CoevalStream.nextSeqS(List(3), Coeval.now(CoevalStream.halt[Int](None)), stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    firstNext(composed.stream).value match {
      case Streamable.NextSeq(head, _, stop) =>
        assertEquals(head, List(6))
        assertEquals(stop.value, ())
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }
}
