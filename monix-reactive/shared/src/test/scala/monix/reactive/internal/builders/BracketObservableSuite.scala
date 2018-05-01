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

package monix.reactive.internal.builders

import cats.laws._
import monix.eval.Task
import monix.reactive.{BaseTestSuite, Observable}
import cats.laws.discipline._
import monix.execution.BracketResult.{Completed, EarlyStop, Error}
import monix.execution.exceptions.DummyException

object BracketObservableSuite extends BaseTestSuite {

  class Resource(var acquired: Int = 0, var released: Int = 0) {
    def acquire = Task { acquired += 1 }
    def release = Task { released += 1 }
  }

  test("Observable.bracket yields all elements `use` provides") { implicit s =>
    check1 { (source: Observable[Int]) =>
      val bracketed = Observable.bracketA(Task.unit)(
        _ => source,
        (_, _) => Task.unit
      )

      source <-> bracketed
    }
  }

  test("Observable.bracket releases resource on normal completion") { implicit s =>
    val rs = new Resource
    val bracketed = Observable.bracketA(rs.acquire)(
      _ => Observable.range(1, 10),
      (_, result) => rs.release.flatMap(_ => Task {
        assertEquals(result, Completed)
      })
    )
    bracketed.completedL.runAsync
    s.tick()

    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Observable.bracket releases resource on early stop") { implicit s =>
    val rs = new Resource
    val bracketed = Observable.bracketA(rs.acquire)(
      _ => Observable.range(1, 10),
      (_, result) => rs.release.flatMap(_ => Task {
        assertEquals(result, EarlyStop)
      })
    ).take(1)
    bracketed.completedL.runAsync
    s.tick()

    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Observable.bracket releases resource on exception") { implicit s =>
    val rs = new Resource
    val error = DummyException("dummy")
    val bracketed = Observable.bracketA(rs.acquire)(
      _ => Observable.range(1, 10) ++ Observable.raiseError[Long](error),
      (_, result) => rs.release.flatMap(_ => Task {
        assertEquals(result, Error(error))
      })
    )
    bracketed.completedL.runAsync
    s.tick()

    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Observable.bracket releases resource if `use` throws") { implicit s =>
    val rs = new Resource
    val dummy = DummyException("dummy")
    val bracketed = Observable.bracketA(rs.acquire)(
      _ => throw dummy,
      (_, result) => rs.release.flatMap(_ => Task {
        assertEquals(result, Error(dummy))
      })
    )
    intercept[DummyException] {
      bracketed.completedL.runAsync
      s.tick()
    }
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

//  test("Observable.bracket does not call `release` if `acquire` has an error") { implicit s =>
//    val rs = new Resource
//    val dummy = DummyException("dummy")
//    val bracketed = Observable.bracketA(
//      Task.raiseError(dummy).flatMap(_ => rs.acquire))(
//      _ => Observable.empty[Int],
//      (_, result) => rs.release.flatMap(_ => Task {
//        assertEquals(result, Error(dummy))
//      })
//    )
//    intercept[DummyException] {
//      bracketed.completedL.runAsync
//      s.tick()
//    }
//
//    assertEquals(rs.acquired, 1)
//    assertEquals(rs.released, 1)
//  }
//
//  test("Observable.bracket nesting: outer releases even if inner release fails") { implicit s =>
//    var released = false
//    val dummy = DummyException("dummy")
//    val bracketed = Observable.bracketA(Task.unit)(
//      _ => Observable.bracketA(Task.unit)(
//        _ => Observable(1, 2, 3),
//        (_, _) => Task.raiseError(dummy)
//      ),
//      (_, _) => Task { released = true }
//    )
//    intercept[DummyException] {
//      bracketed.completedL.runAsync
//      s.tick()
//    }
//
//    assert(released)
//  }
//
//  test("Observable.bracket nesting: inner releases even if outer release fails") { implicit s =>
//    var released = false
//    val dummy = DummyException("dummy")
//    val bracketed = Observable.bracketA(Task.unit)(
//      _ => Observable.bracketA(Task.unit)(
//        _ => Observable(1, 2, 3),
//        (_, _) => Task { released = true }
//      ),
//      (_, _) => Task.raiseError(dummy)
//    )
//    intercept[DummyException] {
//      bracketed.completedL.runAsync
//      s.tick()
//    }
//
//    assert(released)
//  }

}
