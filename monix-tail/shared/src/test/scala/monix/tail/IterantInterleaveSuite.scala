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
import cats.effect.Sync
import monix.eval.Coeval
import monix.execution.internal.Platform
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

object IterantInterleaveSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  private def naiveImp[F[_], A, B >: A](lh: Iterant[F, A], rh: Iterant[F, B])
                                       (implicit F: Sync[F]): Iterant[F, B] =
    lh.zip(rh).flatMap { case (a, b) => Iterant[F].pure(a) ++ Iterant[F].pure(b) }

  test("naiveImp on iterants equivalence with List-based one") { implicit s =>
    check4 { (list1: List[Int], idx1: Int, list2: List[Int], idx2: Int) =>
      val stream1 = arbitraryListToIterant[Coeval, Int](list1, math.abs(idx1) + 1, allowErrors = false)
      val stream2 = arbitraryListToIterant[Coeval, Int](list2, math.abs(idx2) + 1, allowErrors = false)

      val expected = Coeval(list1.zip(list2).flatMap { case (a, b) => List(a, b) }).value
      naiveImp(stream1, stream2).toListL.value <-> expected
    }
  }

  test("Iterant.interleave equivalence with naiveImp") { implicit s =>
    check4 { (list1: List[Int], idx1: Int, list2: List[Int], idx2: Int) =>
      val stream1 = arbitraryListToIterant[Coeval, Int](list1, math.abs(idx1) + 1, allowErrors = false)
      val stream2 = arbitraryListToIterant[Coeval, Int](list2, math.abs(idx2) + 1, allowErrors = false)

      stream1.interleave(stream2).toListL.value <-> naiveImp(stream1, stream2).toListL.value
    }
  }

  // TODO various scenarios (stop on error, etc.)

}
