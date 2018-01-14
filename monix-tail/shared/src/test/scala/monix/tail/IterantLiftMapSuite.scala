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

import cats.~>
import cats.effect.IO
import cats.laws._
import cats.laws.discipline._
import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import scala.util.Failure

object IterantLiftMapSuite extends BaseTestSuite {
  test("liftMap(f, g) converts Iterant[Coeval, ?] to Iterant[Task, ?]") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val source = arbitraryListToIterant[Coeval, Int](list, idx)
      val expected = arbitraryListToIterant[Task, Int](list, idx)

      val r = source.liftMap(_.task, _.task)
      r <-> expected
    }
  }

  test("liftMap(f, g) converts Iterant[Task, ?] to Iterant[IO, ?]") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val source = arbitraryListToIterant[Task, Int](list, idx)
      val expected = arbitraryListToIterant[IO, Int](list, idx)

      val r = source.liftMap(_.toIO, _.toIO)
      r <-> expected
    }
  }

  test("liftMap(f, g) protects against errors in f") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val source = Iterant[Coeval].of(1, 2, 3).doOnEarlyStop(Coeval { effect += 1 })
    val r = source.liftMap[Coeval](_ => throw dummy, x => x)

    assertEquals(r.completeL.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("liftMapK(f) converts Iterant[Coeval, ?] to Iterant[Task, ?]") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val source = arbitraryListToIterant[Coeval, Int](list, idx)
      val expected = arbitraryListToIterant[Task, Int](list, idx)

      val r = source.liftMapK(new (Coeval ~> Task) {
        def apply[A](fa: Coeval[A]): Task[A] =
          fa.task
      })

      r <-> expected
    }
  }

  test("liftMapK(f) converts Iterant[Task, ?] to Iterant[IO, ?]") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val source = arbitraryListToIterant[Task, Int](list, idx)
      val expected = arbitraryListToIterant[IO, Int](list, idx)

      val r = source.liftMapK(new (Task ~> IO) {
        def apply[A](fa: Task[A]): IO[A] =
          fa.toIO
      })

      r <-> expected
    }
  }
}
