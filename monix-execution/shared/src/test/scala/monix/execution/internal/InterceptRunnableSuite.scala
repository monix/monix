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
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TrampolinedRunnable

object InterceptRunnableSuite extends SimpleTestSuite {
  private val TestException = DummyException("dummy")

  private val silentReporter = UncaughtExceptionReporter(_ => ())

  private def throwing: Runnable = () => throw TestException
  private def throwingTrampolined = new TrampolinedRunnable { def run(): Unit = throw TestException }

  test("reports a failure to the handler") {
    var reported: Throwable = null
    val runnable = InterceptRunnable(throwing, UncaughtExceptionReporter(failure => reported = failure))

    runnable.run()

    assertEquals(reported, TestException)
  }

  test("reports a failure of a TrampolinedRunnable, keeping it trampolined") {
    var reported: Throwable = null
    val runnable =
      InterceptRunnable(throwingTrampolined, UncaughtExceptionReporter(failure => reported = failure))

    assert(runnable.isInstanceOf[TrampolinedRunnable], "runnable.isInstanceOf[TrampolinedRunnable]")
    runnable.run()

    assertEquals(reported, TestException)
  }

  test("leaves the runnable untouched when there is no handler") {
    val plain = throwing
    val trampolined = throwingTrampolined

    assert(InterceptRunnable(plain, null) eq plain, "a plain runnable is returned as it is")
    assert(InterceptRunnable(trampolined, null) eq trampolined, "a trampolined runnable is returned as it is")
  }

  test("does not intercept an already intercepted runnable") {
    val intercepted = InterceptRunnable(throwing, silentReporter)

    assert(InterceptRunnable(intercepted, silentReporter) eq intercepted)
  }
}
