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

import cats.effect.IO
import cats.laws._
import cats.laws.discipline._
import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import monix.tail.batches.{Batch, BatchCursor}

object IterantOnErrorSuite extends BaseTestSuite {
  test("fa.attempt <-> fa.map(Right) for successful streams") { implicit s =>
    val i = Iterant[Coeval].of(1, 2, 3)

    assertEquals(
      i.attempt.toListL.value,
      i.map(Right.apply).toListL.value
    )
  }

  test("fa.attempt ends with a Left in case of error") { implicit s =>
    val dummy = DummyException("dummy")
    val i = Iterant[Coeval].of(1, 2, 3) ++ Iterant[Coeval].raiseError[Int](dummy)

    assertEquals(
      i.attempt.toListL.value,
      List(Right(1), Right(2), Right(3), Left(dummy))
    )
  }

  test("fa.attempt.flatMap <-> fa") { implicit s =>
    check1 { (fa: Iterant[Coeval, Int]) =>
      val fae = fa.attempt
      val r = fae.flatMap(_.fold(
        e => Iterant[Coeval].raiseError[Int](e),
        a => Iterant[Coeval].pure(a)
      )
      )

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
      iter1.onErrorHandleWith(_ => iter2).toListL.value,
      List(1, 2, 3, 4, 5, 6)
    )
  }

  test("Iterant[Task].onErrorHandleWith should protect against broken batches") { implicit s =>
    check1 { (prefix: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val cursor = new ThrowExceptionCursor(dummy)
      val error = Iterant[Task].nextCursorS(cursor, Task.now(Iterant[Task].empty[Int]), Task.unit)
      val stream = (prefix.onErrorIgnore ++ error).onErrorHandleWith(ex => Iterant[Task].haltS[Int](Some(ex)))
      stream <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  def brokenTails: Array[Iterant[Coeval, Int]] = {
    val dummy = DummyException("dummy")

    def withError(ctor: (Coeval[Iterant[Coeval, Int]], Coeval[Unit]) => Iterant[Coeval, Int]) = {
      ctor(Coeval.raiseError(dummy), Coeval.unit)
    }

    Array(
      withError(Iterant.suspendS),
      withError(Iterant.nextS(0, _, _)),
      withError(Iterant.nextBatchS(Batch(0), _, _)),
      withError(Iterant.nextCursorS(BatchCursor(0), _, _))
    )
  }

  test("onErrorHandleWith should protect against broken continuations") { _ =>
    val fallback = Seq(1, 2, 3)
    for (broken <- brokenTails) {
      val out = broken
        .onErrorHandleWith(_ => Iterant[Coeval].fromSeq(fallback))
        .toListL
        .value

      assertEquals(out.takeRight(fallback.length), fallback)
    }
  }

  test("onErrorHandleWith should execute earlyStop of stream prior to error in continuation") { _ =>
    var effect = 0

    val errorInTail = Iterant[Coeval].nextS(1,
      Coeval {
        Iterant[Coeval].nextS(2,
          Coeval { (throw DummyException("Dummy")) : Iterant[Coeval, Int]},
          Coeval { effect = 2 }
        )
      },
      Coeval { effect = 1 }
    )
    errorInTail.onErrorHandleWith(_ => Iterant[Coeval].empty[Int])
      .completeL.value
    assertEquals(effect, 2)
  }

  test("attempt should protect against broken continuations") { _ =>
    for (broken <- brokenTails) {
      val end = broken.attempt
        .toListL
        .value
        .last

      assertEquals(end, Left(DummyException("dummy")))
    }
  }

  test("attempt should execute earlyStop of stream prior to error in continuation") { _ =>
    var effect = 0

    val errorInTail = Iterant[Coeval].nextS(1,
      Coeval {
        Iterant[Coeval].nextS(2,
          Coeval { (throw DummyException("Dummy")) : Iterant[Coeval, Int]},
          Coeval { effect = 2 }
        )
      },
      Coeval { effect = 1 }
    )
    errorInTail.attempt.completeL.value
    assertEquals(effect, 2)
  }

  test("onErrorIgnore should capture exceptions from eval, mapEval & liftF") { _ =>
    val dummy = DummyException("dummy")
    Iterant[IO]
      .eval { throw dummy }
      .onErrorIgnore.completeL.unsafeRunSync()

    Iterant[IO].of(1)
      .mapEval(_ => IO { throw dummy })
      .onErrorIgnore.completeL.unsafeRunSync()

    Iterant[IO].of(1)
      .mapEval(_ => throw dummy)
      .onErrorIgnore.completeL.unsafeRunSync()

    Iterant[IO].liftF(IO { throw dummy })
      .onErrorIgnore.completeL.unsafeRunSync()
  }

  test("attempt should capture exceptions from mapEval") { _ =>
    val dummy = DummyException("dummy")
    val result = Iterant[IO].of(1)
      .mapEval(_ => IO(throw dummy))
      .attempt.headOptionL.unsafeRunSync()

    assertEquals(result, Some(Left(dummy)))
  }
}