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

import java.util.concurrent.CancellationException

import cats.laws._
import cats.laws.discipline._
import monix.execution.exceptions.DummyException

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TaskCancellationSuite extends BaseTestSuite {
  test("cancellation works for async actions") { implicit ec =>
    var wasCancelled = false
    val task = Task.eval(1).delayExecution(1.second)
      .doOnCancel(Task.eval { wasCancelled = true })
      .cancel

    task.runAsync
    assert(wasCancelled, "wasCancelled")
    assert(ec.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("cancellation works for autoCancelableRunLoops") { implicit ec =>
    implicit val opts = Task.defaultOptions.enableAutoCancelableRunLoops

    var effect = 0
    val task = Task(1).flatMap(x => Task(2).map(_ + x))
      .foreachL { x => effect = x }
      .cancel

    val f = task.runAsyncOpt
    ec.tick()
    assertEquals(f.value, Some(Success(())))
    assertEquals(effect, 0)
  }

  test("task.fork.flatMap(fa => fa.cancel.flatMap(_ => fa)) <-> Task.never") { implicit ec =>
    check1 { (task: Task[Int]) =>
      val fa = for {
        forked <- task.asyncBoundary.cancelable.fork
        _ <- forked.cancel
        r <- forked
      } yield r

      fa <-> Task.never
    }
  }

  test("uncancelable works for async actions") { implicit ec =>
    var effect = 0
    val task = Task.eval(1).delayExecution(1.second)
      .foreachL { x => effect += x }

    val f = task.uncancelable.flatMap(_ => task).runAsync
    ec.tick()
    assertEquals(effect, 0)

    f.cancel()
    ec.tick(1.second)
    assertEquals(effect, 1)

    assert(ec.state.tasks.isEmpty, "tasks.isEmpty")
    ec.tick(1.second)
    assertEquals(f.value, None)
  }

  test("uncancelable works for autoCancelableRunLoops") { implicit ec =>
    val task = Task(1)
    val source = task.flatMap(x => task.map(_ + x))
      .executeWithOptions(_.enableAutoCancelableRunLoops)

    val f1 = source.uncancelable.runAsync
    val f2 = source.runAsync

    f1.cancel()
    f2.cancel()

    ec.tick()
    assertEquals(f1.value, Some(Success(2)))
    assertEquals(f2.value, None)
  }

  test("uncancelable is stack safe in flatMap loop, take 1") { implicit ec =>
    def loop(n: Int): Task[Int] =
      Task.eval(n).flatMap { x =>
        if (x > 0)
          Task.eval(x - 1).uncancelable.flatMap(loop)
        else
          Task.pure(0)
      }

    val f = loop(10000).runAsync
    ec.tick()
    assertEquals(f.value, Some(Success(0)))
  }

  test("uncancelable is stack safe in flatMap loop, take 2") { implicit ec =>
    var task = Task(1)
    for (_ <- 0 until 10000) task = task.uncancelable

    val f = task.runAsync
    ec.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("fa.onCancelRaiseError <-> fa") { implicit ec =>
    val dummy = new DummyException("dummy")
    check1 { (fa: Task[Int]) =>
      fa.onCancelRaiseError(dummy) <-> fa
    }
  }

  test("fa.onCancelRaiseError(e).fork.flatMap(fa => fa.cancel.flatMap(_ => fa)) <-> raiseError(e)") { implicit ec =>
    check2 { (fa: Task[Int], e: Throwable) =>
      val received = fa
        .onCancelRaiseError(e)
        .fork
        .flatMap(fa => fa.cancel.flatMap(_ => fa))

      received <-> Task.raiseError(e)
    }
  }

  test("errors raised after cancel get reported") { implicit sc =>
    val dummy = new DummyException()
    val canceled = new CancellationException()
    val task = Task.raiseError[Int](dummy)
      .executeAsync
      .onCancelRaiseError(canceled)

    val f = task.runAsync
    f.cancel()
    sc.tick()

    assertEquals(f.value, Some(Failure(canceled)))
    assertEquals(sc.state.lastReportedError, dummy)
  }
}
