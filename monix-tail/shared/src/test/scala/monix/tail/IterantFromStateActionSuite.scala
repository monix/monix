/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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
import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform.recommendedBatchSize
import monix.tail.Iterant.NextBatch

object IterantFromStateActionSuite extends BaseTestSuite {
  test("Iterant.fromStateAction should evolve state") { implicit s =>
    check3 { (seed: Int, f: Int => (Int, Int), i: Int) =>
      val n = i % (recommendedBatchSize * 2)
      val stream = Iterant[Task].fromStateAction[Int, Int](f)(seed)
      val expected = Iterator
        .continually(0)
        .scanLeft(f(seed)) { case ((_, newSeed), _) => f(newSeed) }
        .map { case (value, _) => value }
        .take(n)
        .toList

      stream.take(n).toListL <-> Task.now(expected)
    }
  }

  test("Iterant.fromStateAction should emit NextBatch items") { implicit s =>
    val stream = Iterant[Task].fromStateAction[Int, Int](seed => (seed, seed))(0)

    assert(stream.isInstanceOf[NextBatch[Task, Int]], "should emit NextBatch items")
  }

  test("Iterant.fromStateAction protects against exceptions initial") { implicit s =>
    val dummy = DummyException("dummy")
    val received = Iterant[Coeval].fromStateAction[Int, Int](e => (e, e))(throw dummy).attempt.toListL

    assertEquals(received.value(), List(Left(dummy)))
  }

  test("Iterant.fromStateAction protects against exceptions in f") { implicit s =>
    val dummy = DummyException("dummy")
    def throwDummy: Int = throw dummy
    val received = Iterant[Coeval].fromStateAction[Int, Int](_ => (throwDummy, throwDummy))(0).attempt.toListL
    assertEquals(received.value(), List(Left(dummy)))
  }

  test("Iterant.fromStateActionL should evolve state") { implicit s =>
    check3 { (seed: Int, f: Int => (Int, Int), i: Int) =>
      val n = i % (recommendedBatchSize * 2)
      val stream = Iterant[Task].fromStateActionL[Int, Int](f andThen Task.now)(Task.now(seed))
      val expected = Iterator
        .continually(0)
        .scanLeft(f(seed)) { case ((_, newSeed), _) => f(newSeed) }
        .map { case (value, _) => value }
        .take(n)
        .toList

      stream.take(n).toListL <-> Task.now(expected)
    }
  }

  test("Iterant.fromStateAction <->  Iterant.fromStateActionL") { implicit s =>
    check3 { (seed: Int, f: Int => (Int, Int), i: Int) =>
      val n = i % (recommendedBatchSize * 2)
      val stream = Iterant[Task].fromStateAction[Int, Int](f)(seed)
      val streamL = Iterant[Task].fromStateActionL[Int, Int](f andThen Task.now)(Task.now(seed))

      stream.take(n) <-> streamL.take(n)
    }
  }

  test("Iterant.fromStateActionL protects against exceptions initial") { implicit s =>
    val dummy = DummyException("dummy")
    val received = Iterant[Coeval].fromStateActionL[Int, Int](e => Coeval.pure((e, e)))(throw dummy)

    check(received <-> Iterant[Coeval].haltS[Int](Some(dummy)))
  }

  test("Iterant.fromStateActionL protects against exceptions in f") { implicit s =>
    val dummy = DummyException("dummy")
    val received = Iterant[Coeval].fromStateActionL[Int, Int](_ => throw dummy)(Coeval.pure(0)).attempt.toListL

    assertEquals(received.value(), List(Left(dummy)))
  }
}
