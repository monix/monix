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

import cats.effect.Sync
import cats.laws._
import cats.laws.discipline._
import monix.eval.{Coeval, Task}
import monix.execution.internal.Platform
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

object IterantTakeEveryNthSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  def naiveImp[F[_], A](iter: Iterant[F, A], takeEveryNth: Int)(implicit F: Sync[F]): Iterant[F, A] =
    iter
      .zipWithIndex
      .flatMap {
        case (a, idx) =>
          if ((idx + 1) % takeEveryNth == 0)
            Iterant[F].pure(a)
          else
            Iterant[F].empty
      }

  test("naiveImp smoke test") { implicit s =>
    val iter = Iterant[Coeval].fromList(List(1, 2, 3, 4, 5, 6))
    assertEquals(naiveImp(iter, 1).toListL.value, List(1, 2, 3, 4, 5, 6))
    assertEquals(naiveImp(iter, 2).toListL.value, List(2, 4, 6))
    assertEquals(naiveImp(iter, 3).toListL.value, List(3, 6))
    assertEquals(naiveImp(iter, 7).toListL.value, List.empty[Int])
  }

  test("Iterant[Task].takeEveryNth equivalence with naiveImp") { implicit s =>
    check3 { (list: List[Int], idx: Int, nr: Int) =>
      val stream = arbitraryListToIterant[Task, Int](list, math.abs(idx) + 1, allowErrors = false)
      val length = list.length
      // scale down from (Int.MinValue to Int.MaxValue) to (1 to (length + 1)) range
      val n = math.round(
        (length * (nr.toDouble - Int.MinValue.toDouble)) / (Int.MaxValue.toDouble - Int.MinValue.toDouble) + 1
      ).toInt
      val res = stream.takeEveryNth(n).toListL.runAsync
      val exp = naiveImp(stream, n).toListL.runAsync
      s.tick()
      res.value <-> exp.value
    }
  }

  // TODO cover various scenarios (early stop, broken batches, etc.)

}
