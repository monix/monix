/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import cats.effect.IO
import cats.syntax.either._
import cats.syntax.eq._
import cats.laws._
import cats.laws.discipline._
import monix.eval.{Coeval, Task}
import monix.execution.exceptions.{CompositeException, DummyException}
import monix.execution.internal.Platform
import monix.tail.batches.{Batch, BatchCursor}

object IterantOnErrorSuite extends BaseTestSuite {
  test("fa.attempt <-> fa.map(Right) for successful streams") { implicit s =>
    val i = Iterant[Coeval].of(1, 2, 3)

    assertEquals(
      i.attempt.toListL.value(),
      i.map(_.asRight).toListL.value()
    )
  }

  test("fa.attempt ends with a Left in case of error") { implicit s =>
    val dummy = DummyException("dummy")
    val i = Iterant[Coeval].of(1, 2, 3) ++ Iterant[Coeval].raiseError[Int](dummy)

    assertEquals(
      i.attempt.toListL.value(),
      List(Right(1), Right(2), Right(3), Left(dummy))
    )
  }

  test("fa.attempt.flatMap <-> fa") { implicit s =>
    check1 { (fa: Iterant[Coeval, Int]) =>
      val fae = fa.attempt
      val r = fae.flatMap(
        _.fold(
          e => Iterant[Coeval].raiseError[Int](e),
          a => Iterant[Coeval].pure(a)
        ))

      r <-> fa
    }
  }

  test("fa.onErrorHandleWith(_ => fb) <-> fa for successful streams") { _ =>
    check1 { (list: List[Int]) =>
      val iter = Iterant[Coeval].of(list: _*)

      iter.onErrorHandleWith(_ => Iterant[Coeval].empty[Int]) <-> iter
    }
  }

  test("fa.onErrorHandleWith(_ => fb) <-> fa ++ fb in case of error") { implicit s =>
    val dummy = DummyException("dummy")
    val iter1 = Iterant[Coeval].of(1, 2, 3) ++ Iterant[Coeval].raiseError[Int](dummy)
    val iter2 = Iterant[Coeval].fromArray(Array(4, 5, 6))

    assertEquals(
      iter1.onErrorHandleWith(_ => iter2).toListL.value(),
      List(1, 2, 3, 4, 5, 6)
    )
  }

  test("Iterant[Task].onErrorHandleWith should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]))
      val stream = (prefix.onErrorIgnore ++ error).onErrorHandleWith(ex => Iterant[Task].haltS[Int](Some(ex)))
      stream <-> prefix.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant[Task].onErrorHandleWith should protect against cursors broken in the middle") { implicit s =>
    val dummy = DummyException("dummy")
    val cursor = BatchCursor.fromIterator(semiBrokenIterator(dummy))
    val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]))
    val result = error.onErrorHandleWith(_ => Iterant[Task].empty[Int])
    assert(result.toListL === Task.now(List(0, 1, 2)))
  }

  def brokenTails: Array[Iterant[Coeval, Int]] = {
    val dummy = DummyException("dummy")

    def withError(ctor: Coeval[Iterant[Coeval, Int]] => Iterant[Coeval, Int]) = {
      ctor(Coeval.raiseError(dummy))
    }

    Array(
      withError(Iterant.suspendS),
      withError(Iterant.nextS(0, _)),
      withError(Iterant.nextBatchS(Batch(0), _)),
      withError(Iterant.nextCursorS(BatchCursor(0), _))
    )
  }

  test("onErrorHandleWith should protect against broken continuations") { _ =>
    val fallback = Seq(1, 2, 3)
    for (broken <- brokenTails) {
      val out = broken
        .onErrorHandleWith(_ => Iterant[Coeval].fromSeq(fallback))
        .toListL
        .value()

      assertEquals(out.takeRight(fallback.length), fallback)
    }
  }

  test("onErrorHandleWith should stack finalizers") { _ =>
    var effect = 0

    val errorInTail = Iterant[Coeval]
      .nextS(
        1,
        Coeval {
          Iterant[Coeval]
            .nextS(2, Coeval { (throw DummyException("Dummy")): Iterant[Coeval, Int] })
            .guarantee(Coeval { effect += 2 })
        })
      .guarantee(Coeval { effect += 1 })

    errorInTail.onErrorHandleWith(_ => Iterant[Coeval].empty[Int]).completedL.value()
    assertEquals(effect, 3)
  }

  test("attempt should protect against broken continuations") { _ =>
    for (broken <- brokenTails) {
      val end = broken.attempt.toListL
        .value()
        .last

      assertEquals(end, Left(DummyException("dummy")))
    }
  }

  test("attempt should stack finalizers") { _ =>
    var effect = 0

    val errorInTail = Iterant[Coeval]
      .nextS(
        1,
        Coeval {
          Iterant[Coeval]
            .nextS(2, Coeval { (throw DummyException("Dummy")): Iterant[Coeval, Int] })
            .guarantee(Coeval { effect += 2 })
        })
      .guarantee(Coeval { effect += 1 })

    errorInTail.attempt.completedL.value()
    assertEquals(effect, 3)
  }

  test("onErrorIgnore works") { _ =>
    check2 { (list: List[Int], idx: Int) =>
      val expected = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = true)
      stream.onErrorIgnore.toListL <-> expected.toListL
    }
  }

  test("onErrorIgnore should capture exceptions from eval, mapEval & liftF") { _ =>
    val dummy = DummyException("dummy")
    Iterant[IO].eval { throw dummy }.onErrorIgnore.completedL.unsafeRunSync()

    Iterant[IO]
      .of(1)
      .mapEval(_ => IO { throw dummy })
      .onErrorIgnore
      .completedL
      .unsafeRunSync()

    Iterant[IO]
      .of(1)
      .mapEval(_ => throw dummy)
      .onErrorIgnore
      .completedL
      .unsafeRunSync()

    Iterant[IO].liftF(IO { throw dummy }).onErrorIgnore.completedL.unsafeRunSync()
  }

  test("attempt should capture exceptions from mapEval") { _ =>
    val dummy = DummyException("dummy")
    val result = Iterant[IO]
      .of(1)
      .mapEval(_ => IO(throw dummy))
      .attempt
      .headOptionL
      .unsafeRunSync()

    assertEquals(result, Some(Left(dummy)))
  }

  test("attempt should protect against broken batches") { _ =>
    val dummy = DummyException("dummy")
    val result =
      Iterant[IO].nextBatchS[Int](ThrowExceptionBatch(dummy), IO(Iterant[IO].empty)).attempt.headOptionL.unsafeRunSync()

    assertEquals(result, Some(Left(dummy)))
  }

  test("attempt should protect against broken cursor") { _ =>
    val dummy = DummyException("dummy")
    val result = Iterant[IO]
      .nextCursorS[Int](ThrowExceptionCursor(dummy), IO(Iterant[IO].empty))
      .attempt
      .headOptionL
      .unsafeRunSync()

    assertEquals(result, Some(Left(dummy)))
  }

  // noinspection ScalaUnreachableCode
  def semiBrokenIterator(ex: Throwable): Iterator[Int] = {
    def end: Iterator[Int] = new Iterator[Int] {
      override def hasNext: Boolean = true
      override def next(): Int = throw ex
    }
    Iterator(0, 1, 2) ++ end
  }

  test("attempt should protect against cursors broken in the middle") { _ =>
    val dummy = DummyException("dummy")
    val cursor = BatchCursor.fromIterator(semiBrokenIterator(dummy))

    val result = Iterant[IO].nextCursorS(cursor, IO(Iterant[IO].empty[Int])).attempt.toListL.unsafeRunSync()

    assertEquals(
      result,
      List(
        Right(0),
        Right(1),
        Right(2),
        Left(dummy)
      ))
  }

  test("Resource.attempt with broken acquire") { _ =>
    val dummy = DummyException("dummy")
    val stream = 1 +: Iterant[Coeval].resource(Coeval.raiseError[Int](dummy))(_ => Coeval.unit)

    assertEquals(
      (stream :+ 2).attempt.toListL.value(),
      Right(1) :: Left(dummy) :: Nil
    )
  }

  test("Resource.attempt with broken use") { _ =>
    val dummy = DummyException("dummy")
    val stream = 1 +: Iterant[Coeval].scopeS[Int, Int](
      Coeval(1),
      _ => Coeval.raiseError[Iterant[Coeval, Int]](dummy),
      (_, _) => Coeval.unit
    )

    assertEquals(
      (stream :+ 2).attempt.toListL.value(),
      Right(1) :: Left(dummy) :: Nil
    )
  }

  test("Resource.attempt with broken release") { _ =>
    val dummy = DummyException("dummy")
    val stream = 1 +: Iterant[Coeval].scopeS[Int, Int](
      Coeval(1),
      i => Coeval(Iterant.pure(i + 1)),
      (_, _) => Coeval.raiseError[Unit](dummy)
    )

    assertEquals(
      (stream :+ 3).attempt.toListL.value(),
      Right(1) :: Right(2) :: Left(dummy) :: Nil
    )
  }

  test("Resource.attempt with broken use and release") { _ =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val stream = 1 +: Iterant[Coeval].scopeS[Int, Int](
      Coeval(1),
      _ => Coeval.raiseError[Iterant[Coeval, Int]](dummy1),
      (_, _) => Coeval.raiseError[Unit](dummy2)
    )

    val list = (stream :+ 2).attempt.toListL.value()
    if (Platform.isJVM) {
      assertEquals(list, Right(1) :: Left(dummy1) :: Nil)
      assertEquals(dummy1.getSuppressed.toList, List(dummy2))
    } else {
      assertEquals(list.length, 2)
      assertEquals(list.head, Right(1))
      val two = list(1)
      assert(two.isLeft && two.swap.forall(_.isInstanceOf[CompositeException]))
    }
  }

  test("Resource.onErrorHandleWith with broken acquire") { _ =>
    val dummy = DummyException("dummy")
    val stream = 1 +: Iterant[Coeval].resource(Coeval.raiseError[Int](dummy))(_ => Coeval.unit)

    assertEquals(
      (stream :+ 2).map(Right(_)).onErrorHandle(Left(_)).toListL.value(),
      Right(1) :: Left(dummy) :: Nil
    )
  }

  test("Resource.onErrorHandleWith with broken use") { _ =>
    val dummy = DummyException("dummy")
    val stream = 1 +: Iterant[Coeval].scopeS[Int, Int](
      Coeval(1),
      _ => Coeval.raiseError[Iterant[Coeval, Int]](dummy),
      (_, _) => Coeval.unit
    )

    assertEquals(
      (stream :+ 2).map(Right(_)).onErrorHandle(Left(_)).toListL.value(),
      Right(1) :: Left(dummy) :: Nil
    )
  }

  test("Resource.onErrorHandleWith with broken release") { _ =>
    val dummy = DummyException("dummy")
    val stream = 1 +: Iterant[Coeval].scopeS[Int, Int](
      Coeval(1),
      i => Coeval(Iterant.pure(i + 1)),
      (_, _) => Coeval.raiseError[Unit](dummy)
    )

    assertEquals(
      (stream :+ 3).map(Right(_)).onErrorHandle(Left(_)).toListL.value(),
      Right(1) :: Right(2) :: Left(dummy) :: Nil
    )
  }

  test("Resource.onErrorHandleWith with broken use and release") { _ =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val stream = 1 +: Iterant[Coeval].scopeS[Int, Int](
      Coeval(1),
      _ => Coeval.raiseError[Iterant[Coeval, Int]](dummy1),
      (_, _) => Coeval.raiseError[Unit](dummy2)
    )

    val list = (stream :+ 2).map(Right(_)).onErrorHandle(Left(_)).toListL.value()
    if (Platform.isJVM) {
      assertEquals(list, Right(1) :: Left(dummy1) :: Nil)
      assertEquals(dummy1.getSuppressed.toList, List(dummy2))
    } else {
      assertEquals(list.length, 2)
      assertEquals(list.head, Right(1))
      val two = list(1)
      assert(two.isLeft && two.swap.forall(_.isInstanceOf[CompositeException]))
    }
  }
}
