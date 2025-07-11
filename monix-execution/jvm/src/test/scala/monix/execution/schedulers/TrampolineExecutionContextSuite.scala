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
import scala.util.control.NoStackTrace

object TrampolineExecutionContextSuite extends SimpleTestSuite {
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

  test("trampoline works for nested executions") {
    final class TestException extends NoStackTrace

    val timeoutMillis = 5000
    val didTimeoutOrFail = new AtomicBoolean(false)
    // 32 to fill 2 chunks of ChunkedArrayQueue, trying to expose a stale reference (e.g. headArray)
    val totalTasks = 32

    def ignoreTestExceptions: UncaughtExceptionReporter = {
      case _: TestException =>
      case x =>
        didTimeoutOrFail.set(true)
        x.printStackTrace()
    }

    val context = TrampolineExecutionContext(Scheduler.io(
      name = "test",
      executionModel = ExecutionModel.AlwaysAsyncExecution,
      reporter = ignoreTestExceptions
    ))

    def executeNestedTrampoline(): Unit = {
      val tasksCounter = new AtomicInteger(0)

      def fail(): Unit = throw new TestException

      def waitForAllExecutions(): Unit = {
        val timeout = System.currentTimeMillis() + timeoutMillis
        while (tasksCounter.get() < totalTasks && System.currentTimeMillis() < timeout) {
          Thread.onSpinWait()
        }
        if (System.currentTimeMillis() >= timeout) {
          // tasks used to get stuck / be skipped in the trampoline run loop,
          // trying to detect it with a timeout
          println(s"Timeout reached, only ${tasksCounter.get()} tasks executed out of $totalTasks")
          didTimeoutOrFail.set(true)
        }
      }

      context.execute {
        () =>
          (1 to totalTasks).foreach { i =>
            context.execute { () =>
              tasksCounter.incrementAndGet()
              fail()
            }
          }
          fail()
      }
      waitForAllExecutions()
    }

    (1 to 1000).foreach { _ =>
      executeNestedTrampoline()
    }
    assert(
      !didTimeoutOrFail.get(),
      "Executions inside the trampoline should not timeout, nor throw unexpected exceptions"
    )
  }
}
