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

import cats.laws._
import cats.laws.discipline._
import cats.effect.Sync
import monix.eval.Coeval
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success}

object IterantFoldRightSuite extends BaseTestSuite {
  def exists(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldRightL(Coeval(false)) { (e, next) =>
      if (p(e)) Coeval(true) else next
    }

  def find(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Option[Int]] =
    ref.foldRightL(Coeval(Option.empty[Int])) { (e, next) =>
      if (p(e)) Coeval(Some(e)) else next
    }

  def forall(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldRightL(Coeval(true)) { (e, next) =>
      if (!p(e)) Coeval(false) else next
    }

  def concat[F[_], A](lh: Iterant[F, A], rh: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {

    Iterant.suspend[F, A] {
      lh.foldRightL(F.pure(rh)) { (a, rest) =>
        F.pure(Iterant.nextS(a, rest))
      }
    }
  }

  test("foldRightL can express existsL") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], p: Int => Boolean) =>
      exists(stream, p) <-> stream.existsL(p)
    }
  }

  test("foldRightL can express forallL") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], p: Int => Boolean) =>
      forall(stream, p) <-> stream.forallL(p)
    }
  }

  test("foldRightL can express ++") { implicit s =>
    check2 { (lh: Iterant[Coeval, Int], rh: Iterant[Coeval, Int]) =>
      concat(lh, rh) <-> lh ++ rh
    }
  }

  test("foldRightL flavored ++ does acquisition and releases in order") { implicit s =>
    val state = Atomic(0)

    val lh = Iterant[Coeval].scopeS[Unit, Int](
      Coeval {
        if (!state.compareAndSet(0, 1))
          throw new IllegalStateException("acquire 1")
      },
      _ => Coeval(Iterant.pure(1)),
      (_, _) =>
        Coeval {
          if (!state.compareAndSet(1, 0))
            throw new IllegalStateException("release 1")
        }
    )

    val rh = Iterant[Coeval].scopeS[Unit, Int](
      Coeval {
        if (!state.compareAndSet(0, 2))
          throw new IllegalStateException("acquire 2")
      },
      _ => Coeval(Iterant.pure(2)),
      (_, _) =>
        Coeval {
          if (!state.compareAndSet(2, 0))
            throw new IllegalStateException("release 2")
        }
    )

    val list = concat(lh, rh).toListL.value()
    assertEquals(list, List(1, 2))
  }

  test("foldRightL can short-circuit for exists") { implicit s =>
    var effect = 0
    val ref = Iterant[Coeval].of(1, 2, 3, 4) ++ Iterant[Coeval].suspend(Coeval {
      effect += 1
      Iterant[Coeval].empty[Int]
    })

    val r1 = exists(ref, _ == 6)
    assertEquals(r1.runTry(), Success(false))
    assertEquals(effect, 1)

    val r2 = exists(ref, _ == 3)
    assertEquals(r2.runTry(), Success(true))
    assertEquals(effect, 1)
  }

  test("foldRightL protects against broken op") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval]
      .of(1, 2, 3)
      .guarantee(Coeval { effect += 1 })
      .foldRightL(Coeval(0))((_, _) => throw dummy)

    assertEquals(effect, 0)
    assertEquals(ref.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldRightL protects against broken cursors") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val ref = Iterant[Coeval]
      .nextCursorS(ThrowExceptionCursor[Int](dummy), Coeval(Iterant[Coeval].empty[Int]))
      .guarantee(Coeval { effect += 1 })
      .foldRightL(Coeval(0))((a, acc) => acc.map(_ + a))

    assertEquals(effect, 0)
    assertEquals(ref.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldRightL protects against broken batches") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val ref = Iterant[Coeval]
      .nextBatchS(ThrowExceptionBatch[Int](dummy), Coeval(Iterant[Coeval].empty[Int]))
      .guarantee(Coeval { effect += 1 })
      .foldRightL(Coeval(0))((a, acc) => acc.map(_ + a))

    assertEquals(effect, 0)
    assertEquals(ref.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("find (via foldRightL) is consistent with List.find") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      find(fa, p) <-> Coeval(list.find(p))
    }
  }

  test("find (via foldRightL) can short-circuit, releasing only acquired resources") { implicit s =>
    var effect = 0

    val ref =
      Iterant[Coeval].of(1, 2, 3).guarantee(Coeval { effect += 1 }) ++
        Iterant[Coeval].of(4, 5, 6).guarantee(Coeval { effect += 1 })

    val r = find(ref, _ == 2).runTry()
    assertEquals(r, Success(Some(2)))
    assertEquals(effect, 1)
  }

  test("find (via foldRightL) releases all resources when full stream is processed") { implicit s =>
    var effect = 0

    val ref =
      Iterant[Coeval].of(1, 2, 3).guarantee(Coeval { effect += 1 }) ++
        Iterant[Coeval].of(4, 5, 6).guarantee(Coeval { effect += 1 })

    val r = find(ref, _ == 10).runTry()
    assertEquals(r, Success(None))
    assertEquals(effect, 2)
  }

  test("find (via foldRightL) protects against user errors") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3).guarantee(Coeval { effect += 1 })
    val r = find(ref, _ => throw dummy).runTry()

    assertEquals(r, Failure(dummy))
    assertEquals(effect, 1)
  }
}
