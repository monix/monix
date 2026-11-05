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

object ReportingRunnableSuite extends SimpleTestSuite {
  private val TestException = DummyException("dummy")
  private val TestFatalError = new LinkageError("dummy-fatal")

  private val failIfReported = UncaughtExceptionReporter { failure =>
    fail(s"unexpected failure reported: $failure")
  }

  private def canceledSchedule = {
    val schedule = BooleanCancelable()
    schedule.cancel()
    schedule
  }

  test("runs the runnable") {
    var executions = 0
    val runnable = ReportingRunnable(() => executions += 1, failIfReported, BooleanCancelable())

    runnable.run()
    runnable.run()

    assertEquals(executions, 2)
  }

  test("reports a failure") {
    var reported: Throwable = null
    val runnable = ReportingRunnable(
      () => throw TestException,
      UncaughtExceptionReporter(failure => reported = failure),
      BooleanCancelable()
    )

    runnable.run()

    assertEquals(reported, TestException)
  }

  test("reports a fatal failure and re-throws") {
    var reported: Throwable = null
    val runnable = ReportingRunnable(
      () => throw TestFatalError,
      UncaughtExceptionReporter(failure => reported = failure),
      BooleanCancelable()
    )

    val thrown = intercept[LinkageError] { runnable.run() }

    assertEquals(thrown, TestFatalError)
    assertEquals(reported, TestFatalError)
  }

  test("leaves a failure of a canceled schedule unreported") {
    var reported: Throwable = null
    val runnable =
      ReportingRunnable(() => throw TestException, UncaughtExceptionReporter(reported = _), canceledSchedule)

    runnable.run()

    assertEquals(reported, null)
  }

  test("leaves a fatal failure of a canceled schedule unreported, but still re-throws it") {
    var reported: Throwable = null
    val runnable =
      ReportingRunnable(() => throw TestFatalError, UncaughtExceptionReporter(reported = _), canceledSchedule)

    val thrown = intercept[LinkageError] { runnable.run() }

    assertEquals(thrown, TestFatalError)
    assertEquals(reported, null)
  }
}
