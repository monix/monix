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

import cats.Monoid
import cats.laws._
import cats.laws.discipline._
import monix.eval.Coeval
import monix.execution.exceptions.DummyException

object IterantScanMapSuite extends BaseTestSuite {

  sealed trait State[+A] {
    def count: Int
  }

  case object Init extends State[Nothing] {
    def count = 0
  }

  case class Current[A](current: A, count: Int) extends State[A]

  implicit val intStateMonoid: Monoid[State[Int]] = new Monoid[State[Int]] {
    def empty: State[Int] = Init

    def combine(x: State[Int], y: State[Int]): State[Int] = {
      (x, y) match {
        case (_, Init) => Init
        case (Current(_, count1), Current(curr, count2)) => Current(curr, count1 + count2)
        case (_, curr@ Current(_, _)) => curr
      }
    }
  }

  test("Iterant.scanMap evolves state") { implicit s =>
    check1 { (source: Iterant[Coeval, Int]) =>

      val scanned = source.scanMap(Current(_, 1): State[Int])

      val fa = scanned
        .takeWhile(_.count < 10)
        .collect { case Current(a, _) => a }

      fa.toListL <-> source.take(10).toListL.map(_.take(9))
    }
  }

  test("Iterant.scanMap equivalence to Iterant.scan") { implicit s =>
    check1 { (source: Iterant[Coeval, Int]) =>

      val scanned1 = source.scanMap(Current(_, 1): State[Int])

      val scanned2 = source.scan(Init : State[Int]) { (acc, a) =>
        acc match {
          case Init => Current(a, 1)
          case Current(_, count) => Current(a, count + 1)
        }
      }

      val fa1 = scanned1
        .takeWhile(_.count < 10)
        .collect { case Current(a, _) => a }

      val fa2 = scanned2
        .takeWhile(_.count < 10)
        .collect { case Current(a, _) => a }

      fa1.toListL <-> fa2.toListL
    }
  }

  test("Iterant.scanMap protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val fa = Iterant[Coeval].of(1, 2, 3)
    val r = fa.scanMap(_ => throw dummy).attempt.toListL
    assertEquals(r.value, List(Left(dummy)))
  }
}