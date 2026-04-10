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

package monix.execution

import java.util.concurrent.Executors

import minitest.SimpleTestSuite
import minitest.laws.Checkers
import monix.execution.schedulers._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.TimeUnit

object FeaturesJVMSuite extends SimpleTestSuite with Checkers {
  test("TestScheduler") {
    val ref = TestScheduler()
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(!ref.features.contains(Scheduler.TRACING))
  }

  test("TracingScheduler") {
    val ref = TracingScheduler(global)
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(ref.features.contains(Scheduler.TRACING))
  }

  test("TracingSchedulerService") {
    val ref = TracingSchedulerService(Scheduler.singleThread("features-test"))
    try {
      assert(ref.features.contains(Scheduler.BATCHING))
      assert(ref.features.contains(Scheduler.TRACING))
    } finally {
      ref.shutdown()
    }
  }

  test("Scheduler.Implicits.global") {
    val ref: Scheduler = Scheduler.global
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(!ref.features.contains(Scheduler.TRACING))
  }

  test("AsyncScheduler(global)") {
    val ref = AsyncScheduler(Scheduler.DefaultScheduledExecutor, Scheduler.global, ExecutionModel.Default)
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(!ref.features.contains(Scheduler.TRACING))
  }

  test("TrampolineScheduler(TracingScheduler(global))") {
    val ref = TrampolineScheduler(TracingScheduler(Scheduler.global), ExecutionModel.Default)
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(ref.features.contains(Scheduler.TRACING))
  }

  test("ExecutorScheduler(Executor") {
    val ref = {
      val ec = Executors.newSingleThreadExecutor()
      ExecutorScheduler(ec, UncaughtExceptionReporter.default, ExecutionModel.Default, Features.empty)
    }
    try {
      assert(ref.features.contains(Scheduler.BATCHING))
      assert(!ref.features.contains(Scheduler.TRACING))
    } finally {
      ref.shutdown()
    }
  }

  test("ExecutorScheduler(ScheduledExecutor") {
    val ref = {
      val ec = Executors.newSingleThreadScheduledExecutor()
      ExecutorScheduler(ec, UncaughtExceptionReporter.default, ExecutionModel.Default, Features.empty)
    }
    try {
      assert(ref.features.contains(Scheduler.BATCHING))
      assert(!ref.features.contains(Scheduler.TRACING))
    } finally {
      ref.shutdown()
    }
  }

  test("ExecutorScheduler(ScheduledExecutor)") {
    val ref = {
      val ec = Executors.newSingleThreadScheduledExecutor()
      ExecutorScheduler(ec, UncaughtExceptionReporter.default, ExecutionModel.Default, Features.empty)
    }
    try {
      assert(ref.features.contains(Scheduler.BATCHING))
      assert(!ref.features.contains(Scheduler.TRACING))
    } finally {
      ref.shutdown()
    }
  }

  test("ReferenceScheduler(global)") {
    val ref = wrapViaReferenceScheduler(Scheduler.global)
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(!ref.features.contains(Scheduler.TRACING))
  }

  test("ReferenceScheduler(TracingScheduler(global))") {
    val ref = wrapViaReferenceScheduler(TracingScheduler(Scheduler.global))
    assert(ref.features.contains(Scheduler.BATCHING))
    assert(ref.features.contains(Scheduler.TRACING))
  }

  def wrapViaReferenceScheduler(ec: Scheduler): Scheduler = {
    val ref = new ReferenceScheduler {
      override def execute(command: Runnable): Unit =
        ec.execute(command)
      override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable =
        ec.scheduleOnce(initialDelay, unit, r)
      override def reportFailure(t: Throwable): Unit =
        ec.reportFailure(t)
      override def executionModel: ExecutionModel =
        ec.executionModel
      override def features: Features =
        ec.features
    }
    ref.withUncaughtExceptionReporter(UncaughtExceptionReporter.default)
  }
}
