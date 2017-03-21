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

import monix.eval.Iterant.{Next, NextGen, NextSeq, Suspend}
import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success}

object IterantFlatMapSuite extends BaseTestSuite {
  test("Iterant.flatMap equivalence with List.flatMap") { implicit s =>
    check2 { (stream: Iterant[Int], f: Int => List[Long]) =>
      val result = stream.flatMap(x => Iterant.fromList(f(x))).toListL
      val expected = stream.toListL.map((list: List[Int]) => list.flatMap(f))
      result === expected
    }
  }

  test("Iterant.flatMap can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant.raiseError[Int](dummy)
    assertEquals(stream, stream.flatMap(x => Iterant.now(x)))
  }

  test("Iterant.next.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant.nextS(1, Task(Iterant.empty[Int]), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant.nextSeq.flatMap guards against direct user code errors") { implicit s =>
    val dummy = DummyException("dummy")
    var isCanceled = false

    val stream = Iterant.nextSeqS(List(1,2,3).iterator, Task(Iterant.empty[Int]), Task { isCanceled = true })
    val result = stream.flatMap[Int](_ => throw dummy).toListL.runAsync

    s.tick()
    assertEquals(result.value, Some(Failure(dummy)))
    assert(isCanceled, "isCanceled should be true")
  }

  test("Iterant.next.flatMap chains stop") { implicit s =>
    def firstNext[A](streamable: Iterant[A]): Task[Iterant[A]] =
      streamable match {
        case Suspend(rest, _) =>
          rest.flatMap(firstNext)
        case ref @ Next(gen, rest, stop) =>
          Task.now(ref)
        case _ =>
          Task.now(streamable)
      }

    var effects = Vector.empty[Int]
    val stop1T = Task.eval { effects = effects :+ 1 }
    val stream1: Iterant[Int] =
      Iterant.nextS(1, Task.now(Iterant.haltS[Int](None)), stop1T)

    val stop2T = Task.eval { effects = effects :+ 2 }
    val stream2: Iterant[Int] =
      Iterant.nextS(2, Task.now(Iterant.haltS[Int](None)), stop2T)

    val stop3T = Task.eval { effects = effects :+ 3 }
    val stream3: Iterant[Int] =
      Iterant.nextS(3, Task.now(Iterant.haltS[Int](None)), stop3T)

    val composed =
      for (x <- stream1; y <- stream2; z <- stream3)
        yield x + y + z

    firstNext(composed).runSyncMaybe match {
      case Right(Iterant.Next(head, _, stop)) =>
        assertEquals(head, 6)
        assertEquals(stop.runSyncMaybe, Right(()))
        assertEquals(effects, Vector(3,2,1))
      case state =>
        fail(s"Invalid state: $state")
    }
  }

  test("Iterant.nextSeq.flatMap chains stop") { implicit s =>
    def firstNext[A](streamable: Iterant[A]): Task[Iterant[A]] =
      streamable match {
        case Suspend(rest, _) =>
          rest.flatMap(firstNext)
        case NextGen(gen, rest, stop) =>
          Task.now(NextSeq(gen.iterator, rest, stop))
        case _ =>
          Task.now(streamable)
      }

    var effects = Vector.empty[Int]
    val stop1T = Task.eval { effects = effects :+ 1 }
    val stream1: Iterant[Int] =
      Iterant.fromList(List(1)).doOnEarlyStop(stop1T)

    val stop2T = Task.eval { effects = effects :+ 2 }
    val stream2: Iterant[Int] =
      Iterant.fromList(List(2)).doOnEarlyStop(stop2T)

    val stop3T = Task.eval { effects = effects :+ 3 }
    val stream3: Iterant[Int] =
      Iterant.fromList(List(3)).doOnEarlyStop(stop3T)

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

  test("Iterant.nextSeq.flatMap works for large lists") { implicit s =>
    val count = 100000
    val list = (0 until count).toList
    val sumTask = Iterant.fromList(list)
      .flatMap(x => Iterant.fromList(List(x,x,x)))
      .foldLeftL(0L)(_+_)

    val f = sumTask.runAsync; s.tick()
    assertEquals(f.value, Some(Success(3 * (count.toLong * (count - 1) / 2))))
  }

  test("Iterant.flatMap should protect against indirect user errors") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToIterant(list, idx)
      val received = source.flatMap(_ => Iterant.raiseError[Int](dummy))
      received === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.flatMap should protect against direct exceptions") { implicit s =>
    check2 { (l: List[Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val list = if (l.isEmpty) List(1) else l
      val source = arbitraryListToIterant(list, idx)
      val received = source.flatMap[Int](_ => throw dummy)
      received === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.flatMap should protect against broken cursors") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionIterator(dummy)
      val error = Iterant.nextSeqS(cursor, Task.now(Iterant.empty[Int]), Task.unit)
      val stream = (prefix ++ error).flatMap(x => Iterant.now(x))
      stream === Iterant.haltS[Int](Some(dummy))
    }
  }

  test("Iterant.flatMap should protect against broken generators") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val generator = new ThrowExceptionIterable(dummy)
      val error = Iterant.nextGenS(generator, Task.now(Iterant.empty[Int]), Task.unit)
      val stream = (prefix ++ error).flatMap(x => Iterant.now(x))
      stream === Iterant.haltS[Int](Some(dummy))
    }
  }
}