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
import monix.eval.Coeval
import monix.execution.internal.Platform
import org.scalacheck.Test
import org.scalacheck.Test.Parameters
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

class IterantInterleaveSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  def interleaveLists[A](lh: List[A], rh: List[A]): List[A] = {
    @tailrec
    def loop(lh: List[A], rh: List[A], acc: ListBuffer[A]): List[A] =
      lh match {
        case x :: xs =>
          acc += x
          loop(rh, xs, acc)
        case Nil =>
          acc.toList
      }

    loop(lh, rh, ListBuffer.empty)
  }

  test("interleaveLists #1") {
    val list1 = List(1, 2, 3, 4)
    val list2 = List(1, 2)

    assertEquals(interleaveLists(list1, list2), List(1, 1, 2, 2, 3))
  }

  test("interleaveLists #2") {
    val list1 = List(1, 2)
    val list2 = List(1, 2, 3)

    assertEquals(interleaveLists(list1, list2), List(1, 1, 2, 2))
  }

  fixture.test("Iterant.interleave equivalence with interleaveLists") { implicit s =>
    check4 { (list1: List[Int], idx1: Int, list2: List[Int], idx2: Int) =>
      val stream1 = arbitraryListToIterant[Coeval, Int](list1, math.abs(idx1) + 1, allowErrors = false)
      val stream2 = arbitraryListToIterant[Coeval, Int](list2, math.abs(idx2) + 1, allowErrors = false)

      stream1.interleave(stream2).toListL.value() <-> interleaveLists(list1, list2)
    }
  }
}
