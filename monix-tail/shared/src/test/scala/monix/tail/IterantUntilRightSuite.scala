/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import monix.eval.Coeval
import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success}

object IterantUntilRightSuite extends BaseTestSuite {
  def exists(fa: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    fa.foldUntilRightL(false) { (default, e) =>
      if (p(e)) Right(true) else Left(default)
    }

  def existsEval(fa: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    fa.foldUntilRightEvalL(Coeval(false)) { (default, e) =>
      Coeval(if (p(e)) Right(true) else Left(default))
    }

  def forall(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldUntilRightL(true) { (default, e) =>
      if (!p(e)) Right(false) else Left(default)
    }

  def forallEval(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldUntilRightEvalL(Coeval(true)) { (default, e) =>
      Coeval { if (!p(e)) Right(false) else Left(default) }
    }

  test("foldUntilRightL can express exists") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      exists(fa, p) <-> Coeval(list.exists(p))
    }
  }

  test("foldUntilRightEvalL can express exists") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      existsEval(fa, p) <-> Coeval(list.exists(p))
    }
  }

  test("foldUntilRightL can express forall") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      forall(fa, p) <-> Coeval(list.forall(p))
    }
  }

  test("foldUntilRightEvalL can express forall") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      forallEval(fa, p) <-> Coeval(list.forall(p))
    }
  }

  test("foldUntilRightL can short-circuit") { implicit s =>
    var effect = 0
    val ref = Iterant[Coeval].of(1, 2, 3, 4).doOnEarlyStop(Coeval { effect += 1 })

    val r1 = exists(ref, _ == 6)
    assertEquals(r1.runTry, Success(false))
    assertEquals(effect, 0)

    val r2 = exists(ref, _ == 3)
    assertEquals(r2.runTry, Success(true))
    assertEquals(effect, 1)
  }

  test("foldUntilRightEvalL can short-circuit") { implicit s =>
    var effect = 0
    val ref = Iterant[Coeval].of(1, 2, 3, 4).doOnEarlyStop(Coeval { effect += 1 })

    val r1 = existsEval(ref, _ == 6)
    assertEquals(r1.runTry, Success(false))
    assertEquals(effect, 0)

    val r2 = existsEval(ref, _ == 3)
    assertEquals(r2.runTry, Success(true))
    assertEquals(effect, 1)
  }

  test("foldUntilRightL protects against broken seed") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .map { x => effect += 1; x }
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightL((throw dummy) : Int)((acc, i) => Left(acc + i))

    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 0)
  }

  test("foldUntilRightL protects against broken op") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightL(0)((_, _) => throw dummy)

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldUntilRightL protects against broken cursors") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].nextCursorS(ThrowExceptionCursor[Int](dummy), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightL(0)((a, e) => Left(a + e))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldUntilRightL protects against broken batches") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].nextBatchS(ThrowExceptionBatch[Int](dummy), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightL(0)((a, e) => Left(a + e))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }
  
  test("foldUntilRightEvalL protects against broken seed") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .map { x => effect += 1; x }
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightEvalL(Coeval.raiseError[Int](dummy))((acc, i) => Coeval(Left(acc + i)))

    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 0)
  }

  test("foldUntilRightEvalL protects against broken op") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightEvalL(Coeval(0))((_, _) => throw dummy)

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldUntilRightEvalL protects against op signaling failure") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightEvalL(Coeval(0))((_, _) => Coeval.raiseError(dummy))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldUntilRightEvalL protects against broken cursors") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].nextCursorS(ThrowExceptionCursor[Int](dummy), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightEvalL(Coeval(0))((a, e) => Coeval(Left(a + e)))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldUntilRightEvalL protects against broken batches") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].nextBatchS(ThrowExceptionBatch[Int](dummy), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldUntilRightEvalL(Coeval(0))((a, e) => Coeval(Left(a + e)))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }
}
