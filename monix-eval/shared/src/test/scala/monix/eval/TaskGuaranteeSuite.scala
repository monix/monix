/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import cats.implicits._
import monix.execution.atomic.Atomic
import monix.execution.exceptions.{ CompositeException, DummyException }
import monix.execution.internal.Platform
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

object TaskGuaranteeSuite extends BaseTestSuite {

  test("finalizer is evaluated on success") { implicit sc =>
    var input = Option.empty[Int]
    val task = Task
      .evalAsync(1)
      .map(_ + 1)
      .guarantee(Task.evalAsync {
        input = Some(1)
      })

    val result = task.runToFuture
    sc.tick()

    assertEquals(input, Some(1))
    assertEquals(result.value, Some(Success(2)))
  }

  test("finalizer is evaluated on error") { implicit sc =>
    var input = Option.empty[Int]
    val dummy = DummyException("dummy")
    val task = Task
      .raiseError(dummy)
      .executeAsync
      .guarantee(Task.evalAsync {
        input = Some(1)
      })

    val result = task.runToFuture
    sc.tick()

    assertEquals(input, Some(1))
    assertEquals(result.value, Some(Failure(dummy)))
  }

  test("if finalizer throws, report finalizer error and signal use error") { implicit sc =>
    val useError = DummyException("useError")
    val finalizerError = DummyException("finalizerError")
    val task = Task.raiseError(useError).guarantee(Task.raiseError(finalizerError))

    val result = task.runToFuture
    sc.tick()

    result.value match {
      case Some(Failure(error)) =>
        if (Platform.isJVM) {
          assertEquals(error, useError)
          error.getSuppressed match {
            case Array(error2) =>
              assertEquals(error2, finalizerError)
            case _ =>
              fail("Unexpected suppressed errors list: " + error.getSuppressed.toList)
          }
        } else
          error match {
            case CompositeException(Seq(`useError`, `finalizerError`)) =>
              () // pass
            case _ =>
              fail(s"Unexpected error: $error")
          }

      case other =>
        fail(s"Unexpected result: $other")
    }
  }

  test("finalizer is evaluated on cancelation (1)") { implicit sc =>
    val effect = Atomic(false)
    val task = Task
      .sleep(10.seconds)
      .guarantee(Task(effect.set(true)))
      .flatMap(_ => Task.sleep(10.seconds))

    val f = task.runToFuture
    sc.tick()
    f.cancel()
    sc.tick()

    assert(effect.get(), "effect.get")
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")

    sc.tick(20.seconds)
    assertEquals(f.value, None)
  }

  test("finalizer is evaluated on cancellation (2)") { implicit sc =>
    val effect = Atomic(false)
    val task = Task.unit
      .guarantee(Task.sleep(10.seconds) *> Task(effect.set(true)))
      .flatMap(_ =>
        Task.Async[Unit]((ctx, cb) => {
          println(ctx.connection)
          cb.onSuccess(())
        })
      )
      .flatMap(_ => Task.sleep(10.seconds))

    val f = task.runToFuture
    sc.tick()
    f.cancel()

    sc.tick(10.seconds)
    assert(effect.get(), "effect.get")
    assert(sc.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(f.value, None)
  }

  test("stack-safety (1)") { implicit sc =>
    def loop(n: Int): Task[Unit] =
      if (n <= 0) Task.unit
      else Task.unit.guarantee(Task.unit).flatMap(_ => loop(n - 1))

    val cycles = if (Platform.isJVM) 100000 else 10000
    val f = loop(cycles).runToFuture
    sc.tick()

    assertEquals(f.value, Some(Success(())))
  }

  test("stack-safety (2)") { implicit sc =>
    val cycles = if (Platform.isJVM) 100000 else 10000
    val task = (0 until cycles).foldLeft(Task.unit) { (acc, _) =>
      acc.flatMap(_ => Task.unit.guarantee(Task.unit))
    }

    val f = task.runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("stack-safety (3)") { implicit sc =>
    val cycles = if (Platform.isJVM) 100000 else 10000
    val task = (0 until cycles).foldLeft(Task.unit) { (acc, _) =>
      acc.guarantee(Task.unit)
    }

    val f = task.runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(())))
  }
}
