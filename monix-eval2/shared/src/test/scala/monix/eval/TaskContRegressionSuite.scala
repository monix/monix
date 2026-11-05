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

import cats.effect.kernel.Fiber
import minitest.SimpleTestSuite
import monix.execution.{ CompletionCallback, ExecutionModel }
import monix.execution.schedulers.TestScheduler
import scala.util.Success

object TaskContRegressionSuite extends SimpleTestSuite {
  test("a canceled cont get can be reused in its cancellation finalizer") {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var resume: CompletionCallback[Int] = null
    var observed: Option[Int] = None

    val source = Task.fromCont[Int, Unit] { (callback, get) =>
      resume = callback
      get.onCancel(get.flatMap(value => Task.delay { observed = Some(value) })).map(_ => ())
    }
    val fiber = startSuspended(source)

    val canceled = fiber.cancel.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(canceled.value, None)
    assertEquals(observed, None)

    resume.onSuccess(42)
    scheduler.tick()

    assertEquals(canceled.value, Some(Success(())))
    assertEquals(observed, Some(42))
  }

  test("a cont signal after cancellation is retained for the finalizer get") {
    implicit val scheduler: TestScheduler =
      TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var resume: CompletionCallback[Int] = null
    var observed: Option[Int] = None

    val source = Task.fromCont[Int, Unit] { (callback, get) =>
      resume = callback
      val signalThenGet = Task
        .delay { callback.onSuccess(42) }
        .flatMap(_ => get)
        .flatMap(value => Task.delay { observed = Some(value) })

      get.onCancel(signalThenGet).map(_ => ())
    }
    val fiber = startSuspended(source)

    val canceled = fiber.cancel.unsafeRunToFuture()
    scheduler.tick()

    assertEquals(canceled.value, Some(Success(())))
    assertEquals(observed, Some(42))
  }

  private def startSuspended(source: Task[Unit])(implicit
    scheduler: TestScheduler
  ): Fiber[Task, Throwable, Unit] = {
    val started = source.start.unsafeRunToFuture()
    scheduler.tick()
    started.value.get.get
  }
}
