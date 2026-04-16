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

package monix.execution.schedulers

import minitest.SimpleTestSuite
import monix.execution.{ ExecutionModel, Scheduler, UncaughtExceptionReporter }

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import scala.concurrent.blocking
import scala.util.control.NoStackTrace

object TrampolineExecutionContextSuite extends SimpleTestSuite {
  private val DoNothing = () => ()

  final class TestException extends NoStackTrace

  test("TrampolineExecutionContext.immediate works") {
    val ctx = TrampolineExecutionContext.immediate
    var effect = 0

    ctx.execute(() => {
      effect += 1

      ctx.execute(() => {
        effect += 1
      })
    })

    assertEquals(effect, 2)

    val _ = intercept[NullPointerException] {
      ctx.execute(() => {
        ctx.execute(() => effect += 1)

        throw null
      })
    }

    assertEquals(effect, 3)
  }

  test("trampoline works for nested executions in async multithreaded context") {
    testNestedExecutions(asyncIoScheduler)
  }

  test("trampoline works for nested executions in async singlethreaded context") {
    testNestedExecutions(reporter =>
      Scheduler.singleThread(name = "test", executionModel = ExecutionModel.AlwaysAsyncExecution, reporter = reporter)
    )
  }

  test("trampoline works for nested executions in sync singlethreaded context") {
    testNestedExecutions(reporter =>
      Scheduler.singleThread(name = "test", executionModel = ExecutionModel.SynchronousExecution, reporter = reporter)
    )
  }

  test("trampoline works for nested executions in immediate context") {
    testAllTasksExecuteCorrectly(context = TrampolineExecutionContext.immediate, onExecute = DoNothing)
  }

  test("trampoline works for nested blocking executions") {
    testNestedExecutions(scheduler = asyncIoScheduler, onExecute = DoNothing, isBlocking = true)
  }

  private def testNestedExecutions(
    scheduler: UncaughtExceptionReporter => Scheduler,
    onExecute: () => Unit = failExecution,
    isBlocking: Boolean = false,
  ): Unit = {
    val didFail = new AtomicBoolean(false)

    val context = TrampolineExecutionContext(scheduler(registerUnexpectedExceptions(didFail)))

    testAllTasksExecuteCorrectly(context, onExecute, isBlocking)
    assert(!didFail.get(), "Unexpected exception occurred")
  }

  def registerUnexpectedExceptions(didFail: AtomicBoolean): UncaughtExceptionReporter = {
    case _: TestException =>
    case x =>
      didFail.set(true)
      x.printStackTrace()
  }

  def failExecution(): Unit = throw new TestException

  private def testAllTasksExecuteCorrectly(
    context: TrampolineExecutionContext,
    onExecute: () => Unit,
    isBlocking: Boolean = false,
  ): Unit = {
    val timeoutMillis = 5000
    // 32 to fill 2 chunks of ChunkedArrayQueue, trying to expose a stale reference (e.g. headArray)
    val totalNestedTasks = 32
    // +1 top level task
    val totalTasks = totalNestedTasks + 1

    def executeNestedTrampoline(): Unit = {
      val tasksCounter = new AtomicInteger(0)

      def waitForAllExecutions(): Unit = {
        val timeout = System.currentTimeMillis() + timeoutMillis
        // tasks used to get stuck / be skipped in the trampoline run loop, so applying a timeout
        while (tasksCounter.get() < totalTasks && System.currentTimeMillis() < timeout) {
          Thread.onSpinWait()
        }
        assert(
          tasksCounter.get() == totalTasks, // assert no skipped / duplicated task executions
          s"Expected $totalTasks tasks to be executed, but ${tasksCounter.get()} were executed"
        )
      }

      def scheduleRegisteredExecution(code: () => Unit = DoNothing): Unit = context.execute { () =>
        code()
        tasksCounter.incrementAndGet()
        onExecute()
      }

      scheduleRegisteredExecution(() =>
        (1 to totalNestedTasks).foreach { _ =>
          if (isBlocking) {
            blocking(scheduleRegisteredExecution())
          } else {
            scheduleRegisteredExecution()
          }
        }
      )
      waitForAllExecutions()
    }

    (1 to 1000).foreach { _ =>
      executeNestedTrampoline()
    }
  }

  private def asyncIoScheduler(reporter: UncaughtExceptionReporter) =
    Scheduler.io(name = "test", executionModel = ExecutionModel.AlwaysAsyncExecution, reporter = reporter)
}
