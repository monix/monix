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

import cats.effect.IO
import cats.laws._
import cats.laws.discipline._
import monix.execution.exceptions.DummyException
import monix.tail.BracketResult._

object IterantBracketSuite extends BaseTestSuite {
  class Resource(var acquired: Int = 0, var released: Int = 0) {
    def acquire = IO { acquired += 1 }
    def release = IO { released += 1 }
  }

  test("Bracket yields all elements `use` provides") { _ =>
    check1 { (source: Iterant[IO, Int]) =>
      val bracketed = Iterant.bracket(IO.unit)(
        _ => source,
        (_, _) => IO.unit
      )

      source <-> bracketed
    }
  }

  test("Bracket preserves earlyStop of stream returned from `use`") { _ =>
    var earlyStopDone = false
    val bracketed = Iterant.bracket(IO.unit)(
      _ => Iterant[IO].of(1, 2, 3).doOnEarlyStop(IO {
        earlyStopDone = true
      }),
      (_, _) => IO.unit
    )
    bracketed.take(1).completeL.unsafeRunSync()
    assert(earlyStopDone)
  }

  test("Bracket releases resource on normal completion") { _ =>
    val rs = new Resource
    val bracketed = Iterant.bracket(rs.acquire)(
      _ => Iterant.range(1, 10),
      (_, result) => rs.release.flatMap(_ => IO {
        assertEquals(result, Completed)
      })
    )
    bracketed.completeL.unsafeRunSync()
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Bracket releases resource on early stop") { _ =>
    val rs = new Resource
    val bracketed = Iterant.bracket(rs.acquire)(
      _ => Iterant.range(1, 10),
      (_, result) => rs.release.flatMap(_ => IO {
        assertEquals(result, EarlyStop)
      })
    ).take(1)
    bracketed.completeL.unsafeRunSync()
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Bracket releases resource on exception") { _ =>
    val rs = new Resource
    val error = DummyException("dummy")
    val bracketed = Iterant.bracket(rs.acquire)(
      _ => Iterant.range[IO](1, 10) ++ Iterant.raiseError[IO, Int](error),
      (_, result) => rs.release.flatMap(_ => IO {
        assertEquals(result, Error(error))
      })
    )
    intercept[DummyException] {
      bracketed.completeL.unsafeRunSync()
    }
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Bracket releases resource if `use` throws") { _ =>
    val rs = new Resource
    val dummy = DummyException("dummy")
    val bracketed = Iterant.bracket(rs.acquire)(
      _ => throw dummy,
      (_, result) => rs.release.flatMap(_ => IO {
        assertEquals(result, Error(dummy))
      })
    )
    intercept[DummyException] {
      bracketed.completeL.unsafeRunSync()
    }
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Bracket does not call `release` if `acquire` has an error") { _ =>
    val rs = new Resource
    val dummy = DummyException("dummy")
    val bracketed = Iterant.bracket(
      IO.raiseError(dummy).flatMap(_ => rs.acquire))(
      _ => Iterant.empty[IO, Int],
      (_, result) => rs.release.flatMap(_ => IO {
        assertEquals(result, Error(dummy))
      })
    )
    intercept[DummyException] {
      bracketed.completeL.unsafeRunSync()
    }
    assertEquals(rs.acquired, 0)
    assertEquals(rs.released, 0)
  }
}
