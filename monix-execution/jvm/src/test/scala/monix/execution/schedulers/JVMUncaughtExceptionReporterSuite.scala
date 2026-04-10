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

import monix.execution.{ Scheduler, UncaughtExceptionReporter }
import java.util.concurrent.Executors

import monix.execution.exceptions.DummyException

object JVMUncaughtExceptionReporterSuite extends UncaughtExceptionReporterBaseSuite {
  testReports("Scheduler(ExecutorService, _)")(Scheduler(Executors.newSingleThreadExecutor(), _))
  testReports("Scheduler.io")(r => Scheduler.io(reporter = r))
  testReports("Scheduler.singleThread")(r => Scheduler.singleThread("test-single-thread", reporter = r))
  testReports("Scheduler.computation")(r => Scheduler.computation(reporter = r))
  testReports("Scheduler.forkJoin")(r => Scheduler.forkJoin(1, 2, reporter = r))
  testReports("Scheduler.cached")(r => Scheduler.cached("test-cached", 1, 5, reporter = r))
  testReports("Scheduler.fixedPool")(r => Scheduler.fixedPool("test-fixed", 1, reporter = r))

  testAsync("UncaughtExceptionReporter.asJava") { p =>
    import Scheduler.Implicits.global

    val e = DummyException("dummy")
    val r = UncaughtExceptionReporter { e => p.success(e); () }.asJava
    r.uncaughtException(null, e)

    for (thrown <- p.future) yield {
      assertEquals(thrown, e)
    }
  }
}
