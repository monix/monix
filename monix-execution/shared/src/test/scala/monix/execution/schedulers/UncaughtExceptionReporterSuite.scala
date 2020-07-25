/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import scala.concurrent.{ExecutionContext, Promise}
import scala.concurrent.duration._
import minitest.TestSuite
import monix.execution.{ExecutionModel, FutureUtils, Scheduler, UncaughtExceptionReporter}

class UncaughtExceptionReporterBaseSuite extends TestSuite[Promise[Throwable]] {
  protected val immediateEC = TrampolineExecutionContext.immediate

  object Dummy extends Throwable
  private[this] val throwRunnable: Runnable = new Runnable {
    def run(): Unit = throw Dummy
  }

  def setup() = Promise[Throwable]()

  def tearDown(env: Promise[Throwable]): Unit = ()
  private[this] def reporter(p: Promise[Throwable]) = UncaughtExceptionReporter { t =>
    p.success(t)
    ()
  }

  def testReports(name: String)(f: UncaughtExceptionReporter => Scheduler) = {
    testAsync(name) { p =>
      f(reporter(p)).execute(throwRunnable)
      FutureUtils.timeout(p.future.collect { case Dummy => }(immediateEC), 500.millis)(Scheduler.global)
    }

    testAsync(name + ".withUncaughtExceptionReporter") { p =>
      f(UncaughtExceptionReporter.default).withUncaughtExceptionReporter(reporter(p)).execute(throwRunnable)
      FutureUtils.timeout(p.future.collect { case Dummy => }(immediateEC), 500.millis)(Scheduler.global)
    }
  }
}

object UncaughtExceptionReporterSuite extends UncaughtExceptionReporterBaseSuite {
  testReports("Scheduler(_, ExecModel)")(Scheduler(_, ExecutionModel.Default))
  testReports("Scheduler(global, _)")(Scheduler(Scheduler.global, _))
  testReports("Scheduler(ExecutionContext, _)")(Scheduler(ExecutionContext.global, _))
  testReports("trampoline(Scheduler(_, ExecModel))")(r => Scheduler.trampoline(Scheduler(r, ExecutionModel.Default)))
  testReports("TracingScheduler(Scheduler(_, ExecModel))")(r => TracingScheduler(Scheduler(r, ExecutionModel.Default)))

}
