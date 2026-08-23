/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import cats.effect.kernel.{ Async, Outcome }
import minitest.SimpleTestSuite
import monix.execution.ExecutionModel
import monix.execution.Scheduler.Implicits.global
import monix.execution.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

object TaskSimpleTest extends SimpleTestSuite {
  def testEffect(name: String)(f: => Task[Unit]): Unit =
    testAsync(name)(f.unsafeRunToFuture())

  testEffect("handleErrorWith recovers from failure") {
    val dummy = new RuntimeException("dummy")

    Task.raiseError[Int](dummy)
      .handleErrorWith(error => Task.pure(if (error eq dummy) 42 else 0))
      .map(value => assertEquals(value, 42))
  }

  test("async registration exceptions become effect errors") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val dummy = new RuntimeException("dummy")
    val result = Task.async0[Int]((_, _) => throw dummy).unsafeRunToFuture()

    scheduler.tick()

    assertEquals(result.value, Some(Failure(dummy)))
    assertEquals(scheduler.state.lastReportedError, null)
  }

  test("async is stack safe in synchronous flatMap loops") {
    implicit val scheduler: TestScheduler = TestScheduler()

    def signal(n: Int): Task[Int] =
      Task.async(callback => callback.onSuccess(n))

    def loop(n: Int, acc: Int): Task[Int] =
      signal(n).flatMap { value =>
        if (value > 0) loop(value - 1, acc + 1)
        else Task.pure(acc)
      }

    val result = loop(10000, 0).unsafeRunToFuture()
    scheduler.tick()

    assertEquals(result.value, Some(Success(10000)))
  }

  test("async0 is stack safe in scheduled flatMap loops") {
    implicit val scheduler: TestScheduler = TestScheduler()

    def signal(n: Int): Task[Int] =
      Task.async0 { (s, callback) =>
        s.execute(() => callback.onSuccess(n))
      }

    def loop(n: Int, acc: Int): Task[Int] =
      signal(n).flatMap { value =>
        if (value > 0) loop(value - 1, acc + 1)
        else Task.pure(acc)
      }

    val result = loop(10000, 0).unsafeRunToFuture()
    scheduler.tick()

    assertEquals(result.value, Some(Success(10000)))
  }

  test("async accepts only the first callback result") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var firstAccepted = false
    var secondAccepted = true

    val result = Task
      .async[Int] { callback =>
        firstAccepted = callback.tryOnSuccess(42)
        secondAccepted = callback.tryOnSuccess(43)
      }
      .unsafeRunToFuture()

    scheduler.tick()

    assert(firstAccepted)
    assert(!secondAccepted)
    assertEquals(result.value, Some(Success(42)))
  }

  test("multiple cancels await one finalization") {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var finalizerRuns = 0
    var finishFinalizer: () => Unit = null

    val started = Task.never
      .onCancel {
        Task.delay { finalizerRuns += 1 }.flatMap { _ =>
          Task.async[Unit] { callback =>
            finishFinalizer = () => callback.onSuccess(())
          }
        }
      }
      .start
      .unsafeRunToFuture()

    scheduler.tick()

    val fiber = started.value.get.get
    val firstCancel = fiber.cancel.unsafeRunToFuture()
    val secondCancel = fiber.cancel.unsafeRunToFuture()

    scheduler.tick()

    assertEquals(finalizerRuns, 1)
    assertEquals(firstCancel.value, None)
    assertEquals(secondCancel.value, None)

    finishFinalizer()
    scheduler.tick()

    assertEquals(firstCancel.value, Some(Success(())))
    assertEquals(secondCancel.value, Some(Success(())))
  }

  test("racePair leaves the losing fiber running") {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    var loserCanceled = false

    val result = F
      .racePair(
        Task.pure(42),
        Task.never[Unit].onCancel(Task.delay { loserCanceled = true })
      )
      .unsafeRunToFuture()

    scheduler.tick()

    result.value match {
      case Some(Success(Left((winner, loser)))) =>
        assert(winner.isSuccess)
        assert(!loserCanceled)

        val canceled = loser.cancel.unsafeRunToFuture()
        scheduler.tick()

        assert(loserCanceled)
        assertEquals(canceled.value, Some(Success(())))
      case other =>
        assert(false, s"unexpected racePair result: $other")
    }
  }

  test("cede reschedules the continuation") {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)

    val result = Async[Task].cede
      .flatMap(_ => Task.pure(42))
      .unsafeRunToFuture()

    assertEquals(result.value, None)
    assert(scheduler.tickOne())
    assertEquals(result.value, None)

    scheduler.tick()

    assertEquals(result.value, Some(Success(42)))
  }

  test("evalOn returns the continuation to the source scheduler") {
    implicit val source: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val target = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var targetRan = false
    var sourceContinuationRan = false

    val result = Async[Task]
      .evalOn(
        Task.delay {
          targetRan = true
          21
        },
        target
      )
      .flatMap { value =>
        Task.delay {
          sourceContinuationRan = true
          value * 2
        }
      }
      .unsafeRunToFuture()

    source.tick()
    assert(!targetRan)

    target.tick()
    assert(targetRan)
    assert(!sourceContinuationRan)
    assertEquals(result.value, None)

    source.tick()

    assert(sourceContinuationRan)
    assertEquals(result.value, Some(Success(42)))
  }

  test("canceling suspended async runs all finalizers in order") {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    var results = List.empty[Int]

    val body = F.async[Nothing] { _ =>
      Task.pure(Some(Task.delay { results ::= 3 }))
    }
    val started = body
      .onCancel(Task.delay { results ::= 2 })
      .onCancel(Task.delay { results ::= 1 })
      .start
      .unsafeRunToFuture()

    scheduler.tick()

    val canceled = started.value.get.get.cancel
      .flatMap(_ => Task.delay(results))
      .unsafeRunToFuture()

    scheduler.tick()

    assertEquals(canceled.value, Some(Success(List(1, 2, 3))))
  }

  test("async registration exceptions after a result are reported") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val dummy = new RuntimeException("dummy")
    val result = Task
      .async0[Int] { (_, callback) =>
        callback.onSuccess(42)
        throw dummy
      }
      .unsafeRunToFuture()

    scheduler.tick()

    assertEquals(result.value, Some(Success(42)))
    assertEquals(scheduler.state.lastReportedError, dummy)
  }

  test("cont body exceptions become effect errors") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val dummy = new RuntimeException("dummy")
    val result = Task
      .cont0[Int, Int]((_, _, _) => throw dummy)
      .unsafeRunToFuture()

    scheduler.tick()

    assertEquals(result.value, Some(Failure(dummy)))
    assertEquals(scheduler.state.lastReportedError, null)
  }

  test("cancel runs an onCancel finalizer") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var finalizerRuns = 0
    val future = Task.never
      .onCancel(Task.delay(finalizerRuns += 1))
      .unsafeRunToFuture()

    scheduler.tick()
    future.cancel()
    scheduler.tick()

    assertEquals(finalizerRuns, 1)
  }

  test("uncancelable defers cancellation until a polled region") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var events = List.empty[String]
    val future = Task
      .uncancelable { poll =>
        Task.delay(events :+= "before")
          .flatMap(_ => Task.canceled)
          .flatMap(_ => Task.delay(events :+= "masked"))
          .flatMap(_ => poll(Task.canceled))
          .flatMap(_ => Task.delay(events :+= "after"))
      }
      .onCancel(Task.delay(events :+= "finalizer"))
      .unsafeRunToFuture()

    scheduler.tick()

    assertEquals(events, List("before", "masked", "finalizer"))
    assertEquals(future.value, None)
  }

  testEffect("start exposes a successful outcome through join") {
    Task.pure(42).start.flatMap(_.join).flatMap {
      case Outcome.Succeeded(result) =>
        result.map(value => assertEquals(value, 42))
      case outcome =>
        Task.delay(fail(s"unexpected outcome: $outcome"))
    }
  }

  test("fiber cancellation awaits finalization and publishes a canceled outcome") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var finalized = false

    val started = Task.never
      .onCancel(Task.delay { finalized = true })
      .start
      .unsafeRunToFuture()

    scheduler.tick()

    assert(started.value.exists(_.isSuccess))
    val fiber = started.value.get.get
    val result = fiber.cancel
      .flatMap(_ => Task.delay(assert(finalized)))
      .flatMap(_ => fiber.join)
      .map(outcome => assert(outcome.isCanceled))
      .unsafeRunToFuture()

    scheduler.tick()

    assertEquals(result.value, Some(Success(())))
  }

  test("uncancelable async suspension cannot be awakened by cancellation") {
    implicit val scheduler: TestScheduler = TestScheduler()

    val started = Task
      .uncancelable(_ => Task.never[Unit])
      .start
      .unsafeRunToFuture()

    scheduler.tick()

    assert(started.value.exists(_.isSuccess))
    val fiber = started.value.get.get
    val result = fiber.cancel.unsafeRunToFuture()

    scheduler.tick()

    assertEquals(result.value, None)
  }

  test("pending cancellation runs after an uncancelable suspension resumes") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var resume: () => Unit = null
    var finalized = false
    val started = Task
      .uncancelable { _ =>
        Task.async0[Unit] { (_, callback) =>
          resume = () => callback.onSuccess(())
        }
      }
      .onCancel(Task.delay { finalized = true })
      .start
      .unsafeRunToFuture()

    scheduler.tick()

    assert(started.value.exists(_.isSuccess))
    assert(resume ne null)
    val fiber = started.value.get.get
    val result = fiber.cancel.flatMap(_ => fiber.join).unsafeRunToFuture()

    scheduler.tick()
    assertEquals(result.value, None)
    assert(!finalized)

    resume()
    scheduler.tick()

    assert(finalized)
    assertEquals(result.value.map(_.map(_.isCanceled)), Some(Success(true)))
  }

  testEffect("Cats Effect Async instance evaluates IO") {
    val F = Async[Task]

    F.flatMap(F.delay(21))(value => F.pure(assertEquals(value * 2, 42)))
  }

  testEffect("simple flatMap") {
    for {
      x1 <- Task.pure(1)
      x2 <- Task.pure(2)
      x3 <- Task.pure(3)
      x4 <- Task.async0[Int] { (sc, cb) =>
        sc.execute(() => cb.onSuccess(4))
      }
      x5 <- Task.delay(5)
      x6 <- Task.cont0[Int, Int] { (sc, cb, get) =>
        Task.delay {
          sc.scheduleOnce(1, TimeUnit.SECONDS, () => cb.onSuccess(3 + 3))
        }.flatMap { _ =>
          get
        }
      }
      x7 <- Task.delay(7)
      x8 <- Task.cont0[Int, Int] { (sc, cb, get) =>
        cb.onSuccess(8)
        get
      }
    } yield {
      assertEquals(
        x1 + x2 + x3 + x4 + x5 + x6 + x7 + x8,
        (1 to 8).sum
      )
    }
  }
}
