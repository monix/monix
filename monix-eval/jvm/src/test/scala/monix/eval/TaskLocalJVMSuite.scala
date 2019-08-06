/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import cats.effect.IO
import minitest.SimpleTestSuite
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.{ExecutionModel, Scheduler}
import monix.execution.misc.Local
import monix.execution.schedulers.TracingScheduler

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration._

object TaskLocalJVMSuite extends SimpleTestSuite {
  def createShift(ec: ExecutionContext): Task[Unit] =
    Task.cancelable0 { (_, cb) =>
      ec.execute(new Runnable { def run() = cb.onSuccess(()) })
      Task.unit
    }

  test("locals get transported with executeOn and shift") {
    import Scheduler.Implicits.traced
    val ec = Scheduler.computation(4, "ec1")
    val ec2 = Scheduler.computation(4, "ec2")

    try {
      val task =
        for {
          local <- TaskLocal(0)
          _     <- local.write(100).executeOn(ec2)
          v1    <- local.read.executeOn(ec)
          _     <- Task.shift(Scheduler.global)
          v2    <- local.read.executeOn(ec2)
          _     <- Task.shift
          v3    <- local.read.executeOn(ec2)
          _     <- createShift(ec2)
          v4    <- local.read
          v5    <- local.read.executeOn(ec)
        } yield v1 :: v2 :: v3 :: v4 :: v5 :: Nil

      val r = task.runSyncUnsafe(Duration.Inf)
      assertEquals(r, List(100, 100, 100, 100, 100))
    } finally {
      ec.shutdown()
      ec2.shutdown()
    }
  }

  test("locals get transported with executeWithModel") {
    import Scheduler.Implicits.traced

    val task =
      for {
        local <- TaskLocal(0)
        _     <- local.write(100).executeWithModel(AlwaysAsyncExecution)
        _     <- Task.shift
        v     <- local.read
      } yield v

    val r = task.runSyncUnsafe(Duration.Inf)
    assertEquals(r, 100)
  }

  test("locals get transported with executeWithOptions") {
    import Scheduler.Implicits.traced

    val task =
      for {
        local <- TaskLocal(0)
        _     <- local.write(100).executeWithOptions(_.enableAutoCancelableRunLoops)
        _     <- Task.shift
        v     <- local.read
      } yield v

    val r = task.runSyncUnsafe(Duration.Inf)
    assertEquals(r, 100)
  }

  test("local.write.executeOn(forceAsync = false) works") {
    import Scheduler.Implicits.traced
    val ec = Scheduler.computation(4, "ec1")

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).executeOn(ec, forceAsync = false)
      _ <- Task.shift
      v <- l.read
    } yield v

    val r = task.runSyncUnsafe(Duration.Inf)
    assertEquals(r, 100)
  }

  test("local.write.executeOn(forceAsync = true) works") {
    import monix.execution.Scheduler.Implicits.traced
    val ec = Scheduler.computation(4, "ec1")

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).executeOn(ec)
      _ <- Task.shift
      v <- l.read
    } yield v

    val r = task.runSyncUnsafe(Duration.Inf)
    assertEquals(r, 100)
  }

  test("local state is encapsulated by Task run loop with TracingScheduler") {
    import monix.execution.Scheduler.Implicits.traced
    val local = TaskLocal(0).memoize

    val task = for {
      l <- local
      x <- l.read
      _ <- l.write(x + 1)
    } yield x

    val runMethods: List[Task[Int] => Int] = List(
      _.executeWithOptions(_.enableLocalContextPropagation).runSyncUnsafe(),
      _.executeAsync.executeWithOptions(_.enableLocalContextPropagation).runSyncUnsafe(),
      _.runSyncUnsafe(),
      _.executeWithOptions(_.enableLocalContextPropagation).to[IO].unsafeRunSync(),
      t => Await.result(t.executeWithOptions(_.enableLocalContextPropagation).runToFuture, 1.second),
      t => Await.result(t.runToFuture, 1.second),
      t => Await.result(t.executeWithOptions(_.enableLocalContextPropagation).to[IO].unsafeToFuture(), 1.second)
    )

    for (method <- runMethods) {
      for (_ <- 1 to 10) method(task)
      val r = method(task)
      assertEquals(r, 0)
    }
  }

  test("local state is encapsulated by Task run loop without TracingScheduler") {
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation
    val local = TaskLocal(0).memoize

    val task = for {
      l <- local
      x <- l.read
      _ <- l.write(x + 1)
    } yield x

    val runMethods: List[Task[Int] => Int] = List(
      _.executeWithOptions(_.enableLocalContextPropagation).runSyncUnsafe(),
      _.executeAsync.executeWithOptions(_.enableLocalContextPropagation).runSyncUnsafe(),
      _.runSyncUnsafeOpt(),
      _.executeWithOptions(_.enableLocalContextPropagation).to[IO].unsafeRunSync(),
      t => Await.result(t.executeWithOptions(_.enableLocalContextPropagation).runToFuture, 1.second),
      t => Await.result(t.runToFutureOpt, 1.second),
      t => Await.result(t.executeWithOptions(_.enableLocalContextPropagation).to[IO].unsafeToFuture(), 1.second)
    )

    for (method <- runMethods) {
      for (_ <- 1 to 10) method(task)
      val r = method(task)
      assertEquals(r, 0)
    }
  }

  testAsync("local state is encapsulated by Task run loop on single thread") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))
      .withExecutionModel(ExecutionModel.AlwaysAsyncExecution)
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    def runAssertion(run: Task[Unit] => Any, method: String): Future[Unit] = {
      val p = Promise[Unit]
      val local = Local(0)
      val task = Task.evalAsync(local := 50).flatMap(_ => Task { p.success(()); () })

      run(task)

      val f = p.future

      f.map(_ => {
        assert(local() == 0, s"received ${local()} != expected 0 in $method")
      })
    }

    for {
      _ <- runAssertion(_.runSyncUnsafeOpt(), "runSyncUnsafe")
      _ <- runAssertion(_.runToFutureOpt, "runToFuture")
      _ <- runAssertion(_.runAsyncOpt(_ => ()), "runAsync")
      _ <- runAssertion(_.runAsyncOptF(_ => ()), "runAsyncF")
      _ <- runAssertion(_.runAsyncAndForgetOpt, "runAsyncAndForget")
      _ <- runAssertion(_.runAsyncUncancelableOpt(_ => ()), "runAsyncUncancelable")
    } yield ()

  }
}
