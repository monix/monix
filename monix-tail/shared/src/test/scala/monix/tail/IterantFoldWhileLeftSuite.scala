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
import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success}

object IterantFoldWhileLeftSuite extends BaseTestSuite {
  def exists(fa: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    fa.foldWhileLeftL(false) { (default, e) =>
      if (p(e)) Right(true) else Left(default)
    }

  def existsEval(fa: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    fa.foldWhileLeftEvalL(Coeval(false)) { (default, e) =>
      Coeval(if (p(e)) Right(true) else Left(default))
    }

  def forall(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldWhileLeftL(true) { (default, e) =>
      if (!p(e)) Right(false) else Left(default)
    }

  def forallEval(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldWhileLeftEvalL(Coeval(true)) { (default, e) =>
      Coeval { if (!p(e)) Right(false) else Left(default) }
    }

  test("foldWhileLeftL is consistent with foldLeftL") { implicit s =>
    check3 { (stream: Iterant[Coeval, Int], seed: Long, op: (Long, Int) => Long) =>
      stream.foldWhileLeftL(seed)((s, e) => Left(op(s, e))) <-> stream.foldLeftL(seed)(op)
    }
  }

  test("foldWhileLeftEvalL is consistent with foldLeftL") { implicit s =>
    check3 { (stream: Iterant[Coeval, Int], seed: Long, op: (Long, Int) => Long) =>
      stream.foldWhileLeftEvalL(Coeval(seed))((s, e) => Coeval(Left(op(s, e)))) <->
        stream.foldLeftL(seed)(op)
    }
  }

  test("foldWhileLeftL can express exists") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      exists(fa, p) <-> Coeval(list.exists(p))
    }
  }

  test("foldWhileLeftEvalL can express exists") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      existsEval(fa, p) <-> Coeval(list.exists(p))
    }
  }

  test("foldWhileLeftL can express forall") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      forall(fa, p) <-> Coeval(list.forall(p))
    }
  }

  test("foldWhileLeftEvalL can express forall") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      forallEval(fa, p) <-> Coeval(list.forall(p))
    }
  }

  test("foldWhileLeftL can short-circuit") { implicit s =>
    var effect = 0
    val ref = Iterant[Coeval].of(1, 2, 3, 4).doOnEarlyStop(Coeval { effect += 1 })

    val r1 = exists(ref, _ == 6)
    assertEquals(r1.runTry, Success(false))
    assertEquals(effect, 0)

    val r2 = exists(ref, _ == 3)
    assertEquals(r2.runTry, Success(true))
    assertEquals(effect, 1)
  }

  test("foldWhileLeftEvalL can short-circuit") { implicit s =>
    var effect = 0
    val ref = Iterant[Coeval].of(1, 2, 3, 4).doOnEarlyStop(Coeval { effect += 1 })

    val r1 = existsEval(ref, _ == 6)
    assertEquals(r1.runTry, Success(false))
    assertEquals(effect, 0)

    val r2 = existsEval(ref, _ == 3)
    assertEquals(r2.runTry, Success(true))
    assertEquals(effect, 1)
  }

  test("foldWhileLeftL protects against broken seed") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .map { x => effect += 1; x }
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftL((throw dummy) : Int)((acc, i) => Left(acc + i))

    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 0)
  }

  test("foldWhileLeftL protects against broken op") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftL(0)((_, _) => throw dummy)

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldWhileLeftL protects against broken cursors") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].nextCursorS(ThrowExceptionCursor[Int](dummy), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftL(0)((a, e) => Left(a + e))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldWhileLeftL protects against broken batches") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].nextBatchS(ThrowExceptionBatch[Int](dummy), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftL(0)((a, e) => Left(a + e))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }
  
  test("foldWhileLeftEvalL protects against broken seed") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .map { x => effect += 1; x }
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftEvalL(Coeval.raiseError[Int](dummy))((acc, i) => Coeval(Left(acc + i)))

    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 0)
  }

  test("foldWhileLeftEvalL protects against broken op") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftEvalL(Coeval(0))((_, _) => throw dummy)

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldWhileLeftEvalL protects against op signaling failure") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftEvalL(Coeval(0))((_, _) => Coeval.raiseError(dummy))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldWhileLeftEvalL protects against broken cursors") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].nextCursorS(ThrowExceptionCursor[Int](dummy), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftEvalL(Coeval(0))((a, e) => Coeval(Left(a + e)))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldWhileLeftEvalL protects against broken batches") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].nextBatchS(ThrowExceptionBatch[Int](dummy), Coeval(Iterant[Coeval].empty[Int]), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .foldWhileLeftEvalL(Coeval(0))((a, e) => Coeval(Left(a + e)))

    assertEquals(effect, 0)
    assertEquals(ref.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("existsL is consistent with List.exists") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      fa.existsL(p) <-> Coeval(list.exists(p))
    }
  }

  test("existsL executes early stop on short-circuit") { implicit s =>
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3, 4, 5).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.existsL(_ == 2).runTry

    assertEquals(r, Success(true))
    assertEquals(effect, 1)
  }


  test("existsL does not execute early stop when full stream is processed") { implicit s =>
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3, 4, 5).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.existsL(_ == 10).runTry

    assertEquals(r, Success(false))
    assertEquals(effect, 0)
  }

  test("existsL protects against user errors") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.existsL(_ => throw dummy).runTry

    assertEquals(r, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("forallL is consistent with List.forall") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      fa.forallL(p) <-> Coeval(list.forall(p))
    }
  }

  test("forallL executes early stop on short-circuit") { implicit s =>
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3, 4, 5).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.forallL(_ == 1).runTry

    assertEquals(r, Success(false))
    assertEquals(effect, 1)
  }

  test("forallL does not execute early stop when full stream is processed") { implicit s =>
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3, 4, 5).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.forallL(_ < 10).runTry

    assertEquals(r, Success(true))
    assertEquals(effect, 0)
  }

  test("forallL protects against user errors") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.forallL(_ => throw dummy).runTry

    assertEquals(r, Failure(dummy))
    assertEquals(effect, 1)
  }
  
  test("findL is consistent with List.find") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      fa.findL(p) <-> Coeval(list.find(p))
    }
  }

  test("findL executes early stop on short-circuit") { implicit s =>
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3, 4, 5).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.findL(_ == 2).runTry

    assertEquals(r, Success(Some(2)))
    assertEquals(effect, 1)
  }


  test("findL does not execute early stop when full stream is processed") { implicit s =>
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3, 4, 5).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.findL(_ == 10).runTry

    assertEquals(r, Success(None))
    assertEquals(effect, 0)
  }

  test("findL protects against user errors") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val ref = Iterant[Coeval].of(1, 2, 3).doOnEarlyStop(Coeval { effect += 1 })
    val r = ref.findL(_ => throw dummy).runTry

    assertEquals(r, Failure(dummy))
    assertEquals(effect, 1)
  }
}
