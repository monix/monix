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

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import minitest.SimpleTestSuite
import monix.execution.{ Cancelable, ExecutionModel, FutureUtils, Scheduler, UncaughtExceptionReporter }

class UncaughtExceptionReporterBaseSuite extends SimpleTestSuite {
  protected val immediateEC: TrampolineExecutionContext = TrampolineExecutionContext.immediate

  object Dummy extends Throwable
  private val throwRunnable: Runnable = () => throw Dummy

  private val taskPeriod = 10.millis

  def testReports(name: String)(createScheduler: UncaughtExceptionReporter => Scheduler): Unit = {
    testAsync(name) {
      testFailureReportedOnce(createScheduler) { (scheduler, runnable) =>
        scheduler.execute(runnable)
        Cancelable.empty
      }
    }

    testAsync(name + " + scheduleOnce") {
      testFailureReportedOnce(createScheduler) { (scheduler, runnable) =>
        scheduler.scheduleOnce(1.milli)(runnable.run())
      }
    }

    testAsync(name + " + scheduleAtFixedRate") {
      testFailureReportedOnce(createScheduler) { (scheduler, runnable) =>
        scheduler.scheduleAtFixedRate(0.millis, taskPeriod)(runnable.run())
      }
    }

    testAsync(name + " + scheduleWithFixedDelay") {
      testFailureReportedOnce(createScheduler) { (scheduler, runnable) =>
        scheduler.scheduleWithFixedDelay(0.millis, taskPeriod)(runnable.run())
      }
    }

    testAsync(name + ".withUncaughtExceptionReporter") {
      val initialReporter = new RecordingReporter
      val newReporter = new RecordingReporter

      withScheduler(createScheduler, initialReporter) { original =>
        original.withUncaughtExceptionReporter(newReporter).execute(throwRunnable)

        assertReportedOnce(newReporter)
          .map(_ => assert(!initialReporter.receivedFirstFailure, "The replaced reporter received a failure"))(
            immediateEC
          )
      }
    }
  }

  private def testFailureReportedOnce(
    createScheduler: UncaughtExceptionReporter => Scheduler
  )(
    schedule: (Scheduler, Runnable) => Cancelable
  ): Future[Unit] = {
    val reporter = new RecordingReporter

    withScheduler(createScheduler, reporter) { scheduler =>
      val task = schedule(scheduler, throwRunnable)

      assertReportedOnce(reporter)
        .transform { result => task.cancel(); result }(immediateEC)
    }
  }

  /** Runs `runOnScheduler` against a fresh scheduler, shutting it down once the assertions are done so that the pools
    * these tests create do not pile up. Schedulers with nothing to shut down are left alone.
    */
  private def withScheduler(
    createScheduler: UncaughtExceptionReporter => Scheduler,
    reporter: UncaughtExceptionReporter
  )(
    runOnScheduler: Scheduler => Future[Unit]
  ): Future[Unit] = {
    val scheduler = createScheduler(reporter)
    runOnScheduler(scheduler).transform { result =>
      scheduler match {
        case service: SchedulerService => service.shutdown()
        case _ => ()
      }
      result
    }(immediateEC)
  }

  private def assertReportedOnce(reporter: RecordingReporter): Future[Unit] = {
    implicit val ec: ExecutionContext = immediateEC

    for {
      _ <- FutureUtils.timeout(reporter.firstFailure.collect { case Dummy => }, 15.seconds)(Scheduler.global)
      _ <- FutureUtils.delayedResult(taskPeriod * 5)(())(Scheduler.global)
    } yield assert(!reporter.receivedSecondFailure, "The failure was reported more than once")
  }
}

object UncaughtExceptionReporterSuite extends UncaughtExceptionReporterBaseSuite {
  testReports("Scheduler(_, ExecModel)")(Scheduler(_, ExecutionModel.Default))
  testReports("Scheduler(global, _)")(Scheduler(Scheduler.global, _))
  testReports("Scheduler(ExecutionContext, _)")(Scheduler(ExecutionContext.global, _))
  testReports("trampoline(Scheduler(_, ExecModel))")(r => Scheduler.trampoline(Scheduler(r, ExecutionModel.Default)))
  testReports("TracingScheduler(Scheduler(_, ExecModel))")(r => TracingScheduler(Scheduler(r, ExecutionModel.Default)))
}
