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

package monix.eval

import java.util.concurrent.CancellationException
import cats.laws._
import cats.laws.discipline._
import cats.syntax.all._
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TaskCancellationSuite extends BaseTestSuite {
  test("cancellation works for async actions") { implicit ec =>
    implicit val opts = Task.defaultOptions.disableAutoCancelableRunLoops

    var wasCancelled = false
    val task = Task
      .eval(1)
      .delayExecution(1.second)
      .doOnCancel(Task.eval { wasCancelled = true })
      .start
      .flatMap(_.cancel)

    task.runToFutureOpt; ec.tick()
    assert(wasCancelled, "wasCancelled")
    assert(ec.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("cancellation works for autoCancelableRunLoops") { implicit ec =>
    implicit val opts = Task.defaultOptions.enableAutoCancelableRunLoops

    var effect = 0
    val task = Task
      .evalAsync(1)
      .flatMap(x => Task.evalAsync(2).map(_ + x))
      .foreachL { x =>
        effect = x
      }
      .start
      .flatMap(_.cancel)

    val f = task.runToFutureOpt
    ec.tick()
    assertEquals(f.value, Some(Success(())))
    assertEquals(effect, 0)
  }

  test("task.start.flatMap(fa => fa.cancel.flatMap(_ => fa)) <-> Task.never") { implicit ec =>
    check1 { (task: Task[Int]) =>
      val fa = for {
        forked <- task.attempt.asyncBoundary
          .executeWithOptions(_.enableAutoCancelableRunLoops) // not strictly needed by default
          .start
        _ <- forked.cancel
        r <- forked.join
      } yield r

      fa <-> Task.never
    }
  }

  test("uncancelable works for async actions") { implicit ec =>
    var effect = 0
    val task = Task.eval(1).delayExecution(1.second).foreachL { x =>
      effect += x
    }

    val f = task.uncancelable.flatMap(_ => task).runToFuture
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
    val task = Task.evalAsync(1)
    val source = task
      .flatMap(x => task.map(_ + x))
      .executeWithOptions(_.enableAutoCancelableRunLoops)

    val f1 = source.uncancelable.runToFuture
    val f2 = source.runToFuture

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

    val f = loop(10000).runToFuture
    ec.tick()
    assertEquals(f.value, Some(Success(0)))
  }

  test("uncancelable is stack safe in flatMap loop, take 2") { implicit ec =>
    var task = Task.evalAsync(1)
    for (_ <- 0 until 10000) task = task.uncancelable

    val f = task.runToFuture
    ec.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("fa.onCancelRaiseError <-> fa") { implicit ec =>
    val dummy = new DummyException("dummy")
    check1 { (fa: Task[Int]) =>
      fa.onCancelRaiseError(dummy) <-> fa
    }
  }

  test("fa.onCancelRaiseError(e).start.flatMap(fa => fa.cancel.flatMap(_ => fa)) <-> raiseError(e)") { implicit ec =>
    check2 { (fa: Task[Int], e: Throwable) =>
      val received = fa
        .onCancelRaiseError(e)
        .start
        .flatMap(fa => fa.cancel.flatMap(_ => fa.join))
        .executeWithOptions(_.disableAutoCancelableRunLoops)

      received <-> Task.raiseError(e)
    }
  }

  test("cancelBoundary happy path") { implicit ec =>
    check1 { (task: Task[Int]) =>
      task <* Task.cancelBoundary <-> task
    }
  }

  test("cancelBoundary execution is immediate") { implicit ec =>
    val task = Task.cancelBoundary *> Task(1)
    val f = task.runToFuture
    assertEquals(f.value, Some(Success(1)))
  }

  test("cancelBoundary is stack safe") { implicit ec =>
    def loop(n: Int): Task[Unit] =
      if (n > 0) Task.cancelBoundary.flatMap(_ => loop(n - 1))
      else Task.pure(())

    val count = if (Platform.isJVM) 10000 else 1000
    val f = loop(count).runToFuture; ec.tick()
    assertEquals(f.value, Some(Success(())))
  }

  test("cancelBoundary cancels") { implicit ec =>
    check1 { (task: Task[Int]) =>
      (Task.cancelBoundary *> task).start
        .flatMap(f => f.cancel *> f.join) <-> Task.never
    }
  }

  test("onCancelRaiseError resets cancellation flag") { implicit ec =>
    implicit val opts = Task.defaultOptions.disableAutoCancelableRunLoops

    val err = DummyException("dummy")
    val task = Task
      .never[Int]
      .onCancelRaiseError(err)
      .onErrorRecoverWith { case `err` => Task.cancelBoundary *> Task(10) }
      .start
      .flatMap(f => f.cancel *> f.join)

    val f = task.runToFutureOpt
    ec.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("errors raised after cancel get reported") { implicit sc =>
    val dummy = new DummyException()
    val canceled = new CancellationException()
    val task = Task
      .raiseError[Int](dummy)
      .executeAsync
      .onCancelRaiseError(canceled)

    val f = task.runToFuture
    f.cancel()
    sc.tick()

    assertEquals(f.value, Some(Failure(canceled)))
    assertEquals(sc.state.lastReportedError, dummy)
  }

  test("onCancelRaiseError is stack safe in flatMap loop, take 1") { implicit ec =>
    val cancel = new RuntimeException
    def loop(n: Int): Task[Int] =
      Task.eval(n).flatMap { x =>
        if (x > 0)
          Task.eval(x - 1).onCancelRaiseError(cancel).flatMap(loop)
        else
          Task.pure(0)
      }

    val f = loop(10000).runToFuture
    ec.tick()
    assertEquals(f.value, Some(Success(0)))
  }

  test("onCancelRaiseError is stack safe in flatMap loop, take 2") { implicit ec =>
    val cancel = new RuntimeException
    def loop(n: Int): Task[Int] =
      Task.eval(n).flatMap { x =>
        if (x > 0)
          Task.eval(x - 1).flatMap(loop).onCancelRaiseError(cancel)
        else
          Task.pure(0)
      }

    val f = loop(10000).runToFuture
    ec.tick()
    assertEquals(f.value, Some(Success(0)))
  }

  testAsync("local.write.uncancelable works") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).uncancelable
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }

  testAsync("local.write.onCancelRaiseError works") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation
    val error = DummyException("dummy")

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).onCancelRaiseError(error)
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }
}
