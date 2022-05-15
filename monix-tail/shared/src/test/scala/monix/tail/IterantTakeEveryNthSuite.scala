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

import cats.effect.Sync
import cats.laws._
import cats.laws.discipline._
import monix.eval.{ Coeval, Task }
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.tail.batches.BatchCursor
import org.scalacheck.Test
import org.scalacheck.Test.Parameters
import scala.util.Failure

object IterantTakeEveryNthSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  def naiveImp[F[_], A](iter: Iterant[F, A], takeEveryNth: Int)(implicit F: Sync[F]): Iterant[F, A] =
    iter.zipWithIndex.flatMap {
      case (a, idx) =>
        if ((idx + 1) % takeEveryNth == 0)
          Iterant[F].pure(a)
        else
          Iterant[F].empty
    }

  test("naiveImp smoke test") { implicit s =>
    val input = List(1, 2, 3, 4, 5, 6)
    val iter = Iterant[Coeval].fromList(input)
    assertEquals(naiveImp(iter, 1).toListL.value(), input)
    assertEquals(naiveImp(iter, 2).toListL.value(), List(2, 4, 6))
    assertEquals(naiveImp(iter, 3).toListL.value(), List(3, 6))
    assertEquals(naiveImp(iter, input.length + 1).toListL.value(), List.empty[Int])
  }

  test("Iterant[Task].takeEveryNth equivalence with naiveImp") { implicit s =>
    check3 { (list: List[Int], idx: Int, nr: Int) =>
      val stream = arbitraryListToIterant[Task, Int](list, math.abs(idx) + 1, allowErrors = false)
      val length = list.length
      // scale down from (Int.MinValue to Int.MaxValue) to (1 to (length + 1)) range
      val n = math
        .round(
          (length * (nr.toDouble - Int.MinValue.toDouble)) / (Int.MaxValue.toDouble - Int.MinValue.toDouble) + 1
        )
        .toInt
      val actual = stream.takeEveryNth(n).toListL.runToFuture
      val expected = naiveImp(stream, n).toListL.runToFuture
      s.tick()
      actual.value <-> expected.value
    }
  }

  test("Iterant.takeEveryNth protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty))
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.takeEveryNth(1)
      received <-> iter.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.takeEveryNth protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty))
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.takeEveryNth(1)
      received <-> iter.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.takeEveryNth triggers early stop on exception") { _ =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      val cancelable = BooleanCancelable()
      val dummy = DummyException("dummy")
      val suffix = Iterant[Coeval].nextCursorS[Int](new ThrowExceptionCursor(dummy), Coeval.now(Iterant[Coeval].empty))
      val stream = (iter.onErrorIgnore ++ suffix).guarantee(Coeval.eval(cancelable.cancel()))
      assertEquals(stream.takeEveryNth(1).toListL.runTry(), Failure(dummy))
      cancelable.isCanceled
    }
  }

  test("Iterant.takeEveryNth throws on invalid n") { implicit s =>
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int]))
    intercept[IllegalArgumentException] {
      source.takeEveryNth(0).completedL.value()
      ()
    }
    ()
  }
}
