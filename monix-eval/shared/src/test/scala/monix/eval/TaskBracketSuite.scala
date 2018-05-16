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

package monix.eval

import cats.laws._
import cats.laws.discipline._
import cats.syntax.all._
import monix.execution.exceptions.{CompositeException, DummyException}
import monix.execution.internal.Platform

import scala.util.{Failure, Success}

object TaskBracketSuite extends BaseTestSuite {
  test("equivalence with onErrorHandleWith") { implicit sc =>
    check2 { (task: Task[Int], f: Throwable => Task[Unit]) =>
      val expected = task.onErrorHandleWith(e => f(e) *> Task.raiseError(e))
      val received = task.bracketE(Task.now) {
        case (_, Left(Some(e))) => f(e)
        case (_, _) => Task.unit
      }
      received <-> expected
    }
  }

  test("equivalence with flatMap + transformWith") { implicit sc =>
    check3 { (acquire: Task[Int], f: Int => Task[Int], release: Int => Task[Unit]) =>
      val expected = acquire.flatMap { a =>
        f(a).redeemWith(
          e => release(a) *> Task.raiseError(e),
          s => release(a) *> Task.pure(s)
        )
      }

      val received = acquire.bracket(f)(release)
      received <-> expected
    }
  }

  test("use is protected against user error") { implicit sc =>
    val dummy = new DummyException("dummy")
    var input = Option.empty[(Int, Either[Option[Throwable], Int])]

    val task = Task.evalAsync(1).bracketE(_ => throw dummy) { (a, i) =>
      Task.eval { input = Some((a, i)) }
    }

    val f = task.runAsync
    sc.tick()

    assertEquals(input, Some((1, Left(Some(dummy)))))
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("release is evaluated on success") { implicit sc =>
    var input = Option.empty[(Int, Either[Option[Throwable], Int])]
    val task = Task.evalAsync(1).bracketE(x => Task.evalAsync(x + 1)) { (a, i) =>
      Task.eval { input = Some((a, i)) }
    }

    val f = task.runAsync
    sc.tick()

    assertEquals(input, Some((1, Right(2))))
    assertEquals(f.value, Some(Success(2)))
  }

  test("release is evaluated on error") { implicit sc =>
    val dummy = new DummyException("dummy")
    var input = Option.empty[(Int, Either[Option[Throwable], Int])]

    val task = Task.evalAsync(1).bracketE(_ => Task.raiseError[Int](dummy)) { (a, i) =>
      Task.eval { input = Some((a, i)) }
    }

    val f = task.runAsync
    sc.tick()

    assertEquals(input, Some((1, Left(Some(dummy)))))
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("release is evaluated on cancel") { implicit sc =>
    import scala.concurrent.duration._
    var input = Option.empty[(Int, Either[Option[Throwable], Int])]

    val task = Task.evalAsync(1)
      .bracketE(x => Task.evalAsync(x + 1).delayExecution(1.second)) { (a, i) =>
        Task.eval { input = Some((a, i)) }
      }

    val f = task.runAsync
    sc.tick()

    f.cancel()
    sc.tick(1.second)

    assertEquals(f.value, None)
    assertEquals(input, Some((1, Left(None))))
  }

  test("if both use and release throw, report release error, signal use error") { implicit sc =>
    val useError = new DummyException("use")
    val releaseError = new DummyException("release")

    val task = Task.evalAsync(1).bracket[Int] { _ =>
      Task.raiseError(useError)
    } { _ =>
      Task.raiseError(releaseError)
    }

    val f = task.runAsync
    sc.tick()

    f.value match {
      case Some(Failure(error)) =>
        if (Platform.isJVM) {
          assertEquals(error, useError)
          error.getSuppressed match {
            case Array(error2) =>
              assertEquals(error2, releaseError)
            case _ =>
              fail("Unexpected suppressed errors list: " + error.getSuppressed.toList)
          }
        } else error match {
          case CompositeException(Seq(`useError`, `releaseError`)) =>
            () // pass
          case _ =>
            fail(s"Unexpected error: $error")
        }

      case other =>
        fail(s"Unexpected result: $other")
    }
  }

  test("bracket is stack safe") { implicit sc =>
    def loop(n: Int): Task[Unit] =
      if (n > 0)
        Task(n).bracket(n => Task(n - 1))(_ => Task.unit).flatMap(loop)
      else
        Task.unit

    val count = if (Platform.isJVM) 10000 else 1000
    val f = loop(count).runAsync

    sc.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("bracket works with auto-cancelable run-loops") { implicit sc =>
    import concurrent.duration._

    var effect = 0
    val task = Task(1).bracket(_ => Task.sleep(1.second))(_ => Task(effect += 1))
      .autoCancelable

    val f = task.runAsync
    sc.tick()
    assertEquals(f.value, None)

    f.cancel()
    assertEquals(f.value, None)
    assertEquals(effect, 1)
  }
}
