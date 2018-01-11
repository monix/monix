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

object IterantTakeWhileWithIndexSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  def naiveImp[F[_], A](iter: Iterant[F, A], p: (A, Long) => Boolean)(implicit F: Sync[F]): Iterant[F, A] = {
    var continue = true
    iter
      .zipWithIndex
      .flatMap {
        case (a, idx) =>
          if (p(a, idx) && continue) {
            Iterant[F].pure(a)
          } else {
            continue = false
            Iterant[F].empty
          }
      }
  }

  test("naiveImp smoke test") { implicit s =>
    val input = List(2, 3, 4, 5, 6)
    val iter = Iterant[Coeval].fromList(input)
    assertEquals(naiveImp(iter, (_: Int, _) => true).toListL.value, input)
    assertEquals(naiveImp(iter, (_: Int, idx) => idx != 3).toListL.value, List(2, 3, 4))
    assertEquals(naiveImp(iter, (a: Int, _) => a % 2 == 0).toListL.value, List(2))
    assertEquals(naiveImp(iter, (_: Int, _) => false).toListL.value, List.empty[Int])
  }

  test("Iterant[Task].takeWhileWithIndex((_, _) => true) mirrors the source") { implicit s =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      iter <-> iter.takeWhileWithIndex((_, _) => true)
    }
  }

  test("Iterant[Task].takeWhileWithIndex equivalence with naiveImp") { implicit s =>
    check3 { (list: List[Int], idx: Int, p: (Int, Long) => Boolean) =>
      val stream = arbitraryListToIterant[Task, Int](list, math.abs(idx) + 1, allowErrors = false)
      stream.takeWhileWithIndex(p).toListL <-> naiveImp(stream, p).toListL
    }
  }

  // TODO cover the rest of the scenarios

}
