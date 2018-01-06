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
import cats.effect.IO
import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success}

object IterantBasicSuite extends BaseTestSuite {
  test("arbitraryListToTaskStream works") { implicit s =>
    check2 { (list: List[Int], i: Int) =>
      val stream = arbitraryListToIterant[Task, Int](list, math.abs(i % 4), allowErrors = false)
      stream.toListL <-> Task.now(list)
    }
  }

  test("arbitraryListToCoevalStream") { implicit s =>
    check2 { (list: List[Int], i: Int) =>
      val stream = arbitraryListToIterant[Coeval, Int](list, math.abs(i % 4), allowErrors = false)
      stream.toListL <-> Coeval.now(list)
    }
  }

  test("Iterant.pure") { implicit s =>
    val iter = Iterant[IO].pure(10)
    val f = iter.headOptionL.unsafeToFuture()
    assertEquals(f.value, Some(Success(Some(10))))
  }

  test("Iterant.eval") { implicit s =>
    var effect = 0
    val iter = Iterant[IO].eval { effect += 1; effect }
    val f = iter.foldLeftL(0)(_ + _).unsafeToFuture()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Iterant.defer") { implicit s =>
    var effect = 0
    val iter = Iterant[IO].defer { effect += 1; Iterant[IO].pure(effect) }
    val f = iter.foldLeftL(0)(_ + _).unsafeToFuture()
    assertEquals(f.value, Some(Success(1)))
  }

  test("tailRecM basic usage") { implicit s =>
    val fa = Iterant[Coeval].tailRecM(0) { (a: Int) =>
      if (a < 10)
        Iterant[Coeval].of[Either[Int, Int]](Right(a), Left(a + 1))
      else
        Iterant[Coeval].now[Either[Int, Int]](Right(a))
    }

    val list = fa.toListL.value
    assertEquals(list, (0 to 10).toList)
  }

  test("tailRecM should protect against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val fa = Iterant[Coeval].tailRecM(0) { _ => throw dummy }
    assertEquals(fa.completeL.runTry, Failure(dummy))
  }
}
