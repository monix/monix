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

package monix.execution.internal

import minitest.SimpleTestSuite
import monix.execution.UncaughtExceptionReporter
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.DummyException

object StopOnFailureRunnableSuite extends SimpleTestSuite {
  private val TestException = DummyException("dummy")
  private val TestFatalError = new LinkageError("dummy-fatal")
  private val ReporterFailure = new IllegalStateException("dummy-reporter-failure")

  test("reports a failure and cancels the schedule") {
    var reported: Throwable = null
    val schedule = BooleanCancelable()
    val task = StopOnFailureRunnable(() => throw TestException, UncaughtExceptionReporter(reported = _), schedule)

    task.run()

    assertEquals(reported, TestException)
    assert(schedule.isCanceled, "schedule.isCanceled")
  }

  test("reports a fatal failure, cancels the schedule and re-throws") {
    var reported: Throwable = null
    val schedule = BooleanCancelable()
    val task = StopOnFailureRunnable(() => throw TestFatalError, UncaughtExceptionReporter(reported = _), schedule)

    val thrown = intercept[LinkageError] { task.run() }

    assertEquals(thrown, TestFatalError)
    assertEquals(reported, TestFatalError)
    assert(schedule.isCanceled, "schedule.isCanceled")
  }

  test("cancels the schedule and stops running when the reporter fails") {
    var executions = 0
    val schedule = BooleanCancelable()
    val task = StopOnFailureRunnable(
      () => { executions += 1; throw TestException },
      UncaughtExceptionReporter(_ => throw ReporterFailure),
      schedule
    )

    val thrown = intercept[IllegalStateException] { task.run() }
    task.run()

    assertEquals(thrown, ReporterFailure)
    assert(schedule.isCanceled, "schedule.isCanceled")
    assertEquals(executions, 1)
  }

  test("does not run the runnable again after it failed") {
    var executions = 0
    var reports = 0
    val task = StopOnFailureRunnable(
      () => { executions += 1; throw TestException },
      UncaughtExceptionReporter(_ => reports += 1),
      BooleanCancelable()
    )

    task.run()
    task.run()

    assertEquals(executions, 1)
    assertEquals(reports, 1)
  }
}
