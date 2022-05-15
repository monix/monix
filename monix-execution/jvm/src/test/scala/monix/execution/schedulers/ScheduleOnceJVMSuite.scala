/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.execution
package schedulers

import java.util.concurrent.ScheduledThreadPoolExecutor
import minitest.SimpleTestSuite
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

object ScheduleOnceJVMSuite extends SimpleTestSuite {
  test("Scheduler.global") {
    runTest(Scheduler.global)
  }

  test("Scheduler(global)") {
    runTest(Scheduler(ExecutionContext.global))
  }

  test("Scheduler(scheduledExecutor, global)") {
    val scheduler = createScheduledExecutor()
    try {
      runTest(Scheduler(scheduler, ExecutionContext.global), Some("test-scheduler"))
    } finally {
      scheduler.shutdown()
    }
  }

  test("Scheduler.io") {
    val io: SchedulerService = Scheduler.io()
    try {
      runTest(io)
    } finally {
      io.shutdown()
    }
  }

  test("Scheduler.computation") {
    val sc: SchedulerService = Scheduler.computation()
    try {
      runTest(sc)
    } finally {
      sc.shutdown()
    }
  }

  test("Scheduler.cached") {
    val sc: SchedulerService = Scheduler.cached("cached", 1, 10)
    try {
      runTest(sc)
    } finally {
      sc.shutdown()
    }
  }

  test("Scheduler.singleThread") {
    val sc: SchedulerService = Scheduler.singleThread("singleThread")
    try {
      runTest(sc)
    } finally {
      sc.shutdown()
    }
  }

  test("Scheduler.fixedPool") {
    val sc: SchedulerService = Scheduler.fixedPool("fixedPool", 10)
    try {
      runTest(sc)
    } finally {
      sc.shutdown()
    }
  }

  def runTest(sc: Scheduler, threadPrefix: Option[String] = None): Unit = {
    def runAndGetThread(sc: Scheduler, delayMs: Int): Future[String] = {
      val p = Promise[String]()
      sc.scheduleOnce(
        delayMs.toLong,
        MILLISECONDS,
        () => {
          p.success(Thread.currentThread().getName)
          ()
        })
      p.future
    }

    def runWithDelay(delayMs: Int): Unit = {
      val name = Await.result(runAndGetThread(sc, delayMs), 3.seconds)
      assert(!name.startsWith("monix-scheduler"), "!name.startsWith(\"monix-scheduler\")")
      for (prefix <- threadPrefix) {
        assert(!name.startsWith(prefix), s"!name.startsWith($prefix)")
      }
    }

    runWithDelay(0)
    runWithDelay(-1)
    runWithDelay(10)
  }

  def createScheduledExecutor() = {
    val tp = new ScheduledThreadPoolExecutor(
      1,
      ThreadFactoryBuilder("test-scheduler", UncaughtExceptionReporter.default, daemonic = true)
    )
    tp.setRemoveOnCancelPolicy(true)
    tp
  }
}
