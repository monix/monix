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

object IterantScanEvalSuite extends BaseTestSuite {
  test("scanEval evolves state") { implicit s =>
    sealed trait State[+A] { def count: Int }
    case object Init extends State[Nothing] { def count = 0 }
    case class Current[A](current: Option[A], count: Int) extends State[A]

    case class Person(id: Int, name: String)

    def requestPersonDetails(id: Int): Coeval[Option[Person]] =
      Coeval {
        if (id % 2 == 0) Some(Person(id, s"Person $id"))
        else None
      }

    check2 { (list: List[Int], idx: Int) =>
      val source = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)
      val seed = Coeval.now(Init : State[Person])

      val scanned = source.scanEval(seed) { (state, id) =>
        requestPersonDetails(id).map { person =>
          state match {
            case Init =>
              Current(person, 1)
            case Current(_, count) =>
              Current(person, count + 1)
          }
        }
      }

      val fa = scanned
        .takeWhile(_.count < 20)
        .collect { case Current(Some(p), _) => p.name }
        .toListL

      val expected = source.take(20).toListL.map(ls =>
        ls.take(19)
          .map(x => requestPersonDetails(x).value)
          .collect { case Some(p) => p.name }
      )

      fa <-> expected
    }
  }

  test("scanEval protects against errors in initial") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val fa = Iterant[Coeval].of(1, 2, 3).doOnEarlyStop(Coeval { effect += 1 })
    val r = fa.scanEval(Coeval.raiseError[Int](dummy))((_, e) => Coeval(e)).attempt.toListL

    assertEquals(effect, 0)
    assertEquals(r.value, List(Left(dummy)))
    assertEquals(effect, 1)
  }

  test("scan protects against exceptions in f") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val fa = Iterant[Coeval].of(1, 2, 3).doOnEarlyStop(Coeval { effect += 1 })
    val r = fa.scanEval(Coeval(0))((_, _) => throw dummy).attempt.toListL

    assertEquals(effect, 0)
    assertEquals(r.value, List(Left(dummy)))
    assertEquals(effect, 1)
  }

  test("scan protects against errors in f") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val fa = Iterant[Coeval].of(1, 2, 3).doOnEarlyStop(Coeval { effect += 1 })
    val r = fa.scanEval(Coeval(0))((_, _) => Coeval.raiseError(dummy)).attempt.toListL

    assertEquals(effect, 0)
    assertEquals(r.value, List(Left(dummy)))
    assertEquals(effect, 1)
  }
}
