/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.eval.{ Coeval, Task }
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.tail.batches.{ Batch, BatchCursor }
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

object IterantTakeWhileSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  def takeCount[A](source: Iterant[Coeval, A], n: Int): Iterant[Coeval, A] =
    Iterant.defer {
      var taken = 0
      source.takeWhile { _ =>
        taken += 1
        taken < n
      }
    }

  test("Iterant[Task].takeWhile equivalence with List.takeWhile") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: Int => Boolean) =>
      val iter = arbitraryListToIterant[Task, Int](list, math.abs(idx) + 1, allowErrors = false)
      val stream = iter ++ Iterant[Task].of(1, 2, 3)
      stream.takeWhile(p).toListL <-> stream.toListL.map(_.takeWhile(p))
    }
  }

  test("Iterant[Task].takeWhile works for non-determinate batches") { implicit s =>
    check3 { (list: List[Int], _: Int, p: Int => Boolean) =>
      val stream = Iterant[Task].nextBatchS(Batch.fromIterable(list, 1), Task.now(Iterant[Task].empty[Int]))
      stream.takeWhile(p).toListL <-> stream.toListL.map(_.takeWhile(p))
    }
  }

  test("Iterant[Task].takeWhile(_ => true) mirrors the source") { implicit s =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      iter <-> iter.takeWhile(_ => true)
    }
  }

  test("Iterant[Coeval].takeWhile preserves resource safety") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val cancelable = BooleanCancelable()
      val stream = arbitraryListToIterant[Coeval, Int](list, math.abs(idx) + 1, allowErrors = false)
        .guarantee(Coeval.eval(cancelable.cancel()))

      stream.takeWhile(_ => false).toListL.value() == Nil &&
      (list.length < 2 || cancelable.isCanceled)
    }
  }

  test("Iterant.takeWhile protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty))
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.takeWhile(_ => true)
      received <-> iter.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.takeWhile protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty))
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.takeWhile(_ => true)
      received <-> iter.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.takeWhile protects against user code") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val stream = 1 +: iter.onErrorIgnore

      stream.takeWhile(_ => throw dummy) <-> Iterant[Task].raiseError[Int](dummy)
    }
  }

  test("Iterant.takeWhile preserves resource safety on exception") { _ =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      val cancelable = BooleanCancelable()
      val dummy = DummyException("dummy")
      val suffix = Iterant[Coeval].nextCursorS[Int](new ThrowExceptionCursor(dummy), Coeval.now(Iterant[Coeval].empty))
      val stream = (iter.onErrorIgnore ++ suffix).guarantee(Coeval.eval(cancelable.cancel()))

      intercept[DummyException] { stream.takeWhile(_ => true).toListL.value(): Unit }
      cancelable.isCanceled
    }
  }

  test("Iterant.takeWhile preserves the source guarantee") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source =
      Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int])).guarantee(stop)
    val stream = source.takeWhile(_ => true)
    stream.completedL.value()
    assertEquals(effect, 1)
  }
}
