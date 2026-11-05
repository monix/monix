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

import minitest.SimpleTestSuite
import monix.execution.{ ExecutionModel, Scheduler }
import cats.effect.kernel.{ Async, Outcome }
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

object TaskInlineSchedulerStackSafetySuite extends SimpleTestSuite {
  private val depth = 10000

  test("a long pure chain runs on an inline execution context") {
    val inlineContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = runnable.run()

      override def reportFailure(cause: Throwable): Unit = ()
    }
    val scheduler = Scheduler(inlineContext, ExecutionModel.AlwaysAsyncExecution)

    assertCompletes(scheduler, "inline execution context")
  }

  test("a long pure chain runs on ExecutionContext.parasitic") {
    val scheduler = Scheduler(ExecutionContext.parasitic, ExecutionModel.AlwaysAsyncExecution)

    assertCompletes(scheduler, "ExecutionContext.parasitic")
  }

  test("a long nested start chain runs on an inline synchronous scheduler") {
    val inlineContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = runnable.run()

      override def reportFailure(cause: Throwable): Unit = ()
    }
    val scheduler = Scheduler(inlineContext, ExecutionModel.SynchronousExecution)
    val baseRuns = new AtomicInteger()
    val F = Async[Task]
    var task = Task.delay { val _ = baseRuns.incrementAndGet() }
    var index = 0
    while (index < depth) {
      val previous = task
      task = F.start(previous).flatMap(_.join).flatMap {
        case Outcome.Succeeded(value) => value
        case Outcome.Errored(error) => Task.raiseError(error)
        case Outcome.Canceled() => Task.canceled
      }
      index += 1
    }

    var result: Future[Unit] = null
    var overflow: StackOverflowError = null
    try result = task.unsafeRunToFuture()(scheduler)
    catch {
      case error: StackOverflowError => overflow = error
    }

    assert(
      overflow == null && result != null && result.value.contains(Success(())),
      s"nested starts did not complete: overflow=$overflow, result=${if (result == null) null else result.value}"
    )
    assertEquals(baseRuns.get(), 1)
  }

  private def assertCompletes(scheduler: Scheduler, name: String): Unit = {
    var task = Task.pure(0)
    var index = 0
    while (index < depth) {
      task = task.map(_ + 1)
      index += 1
    }

    var result: Future[Int] = null
    var overflow: StackOverflowError = null
    try result = task.unsafeRunToFuture()(scheduler)
    catch {
      case error: StackOverflowError => overflow = error
    }

    assert(
      overflow == null && result != null && result.value.contains(Success(depth)),
      s"$name did not complete: overflow=$overflow, result=${if (result == null) null else result.value}"
    )
  }
}
