/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import java.util.concurrent.{TimeUnit, CountDownLatch, Executors}
import minitest.SimpleTestSuite
import monix.execution.UncaughtExceptionReporter._
import monix.execution.{UncaughtExceptionReporter, Scheduler}

object TestSchedulerCompanionSuite extends SimpleTestSuite {
  test("scheduler builder, apply, test 1") {
    val service = Executors.newSingleThreadScheduledExecutor()
    val ec = scala.concurrent.ExecutionContext.Implicits.global

    try {
      val latch = new CountDownLatch(2)
      val s = Scheduler(service, ec)
      val r = new Runnable { def run() = latch.countDown() }
      s.execute(r)
      s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
      assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
    } finally {
      service.shutdown()
    }
  }

  test("scheduler builder, apply, test 2") {
    val service = Executors.newSingleThreadScheduledExecutor()
    val ec = scala.concurrent.ExecutionContext.Implicits.global

    try {
      val latch = new CountDownLatch(2)
      val s = Scheduler(service, ec)
      val r = new Runnable { def run() = latch.countDown() }
      s.execute(r)
      s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
      assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
    } finally {
      service.shutdown()
    }
  }

  test("scheduler builder, apply, test 3") {
    val ec = scala.concurrent.ExecutionContext.Implicits.global
    val latch = new CountDownLatch(2)
    val s = Scheduler(ec, UncaughtExceptionReporter(ec.reportFailure))
    val r = new Runnable { def run() = latch.countDown() }
    s.execute(r)
    s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
  }

  test("scheduler builder, apply, test 4") {
    val ec = scala.concurrent.ExecutionContext.Implicits.global
    val latch = new CountDownLatch(2)
    val s = Scheduler(ec)
    val r = new Runnable { def run() = latch.countDown() }
    s.execute(r)
    s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
    assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
  }

  test("scheduler builder, apply, test 5") {
    val service = Executors.newSingleThreadScheduledExecutor()
    val s: SchedulerService = Scheduler(service, LogExceptionsToStandardErr)

    try {
      val latch = new CountDownLatch(2)
      val r = new Runnable { def run() = latch.countDown() }
      s.execute(r)
      s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
      assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
    } finally {
      s.shutdown()
    }
  }

  test("scheduler builder, apply, test 6") {
    val service = Executors.newSingleThreadScheduledExecutor()
    val s: SchedulerService = Scheduler(service)

    try {
      val latch = new CountDownLatch(2)
      val r = new Runnable { def run() = latch.countDown() }
      s.execute(r)
      s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
      assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
    } finally {
      s.shutdown()
    }
  }

  test("scheduler builder, computation") {
    val s: SchedulerService = Scheduler.computation(parallelism=1)
    try {
      val latch = new CountDownLatch(2)
      val r = new Runnable { def run() = latch.countDown() }
      s.execute(r)
      s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
      assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
    } finally {
      s.shutdown()
    }
  }

  test("scheduler builder, io") {
    val s: SchedulerService = Scheduler.io(name="monix-tests-io")
    try {
      val latch = new CountDownLatch(2)
      val r = new Runnable { def run() = latch.countDown() }
      s.execute(r)
      s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
      assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
    } finally {
      s.shutdown()
    }
  }

  test("scheduler builder, single thread") {
    val s: SchedulerService = Scheduler.singleThread(name="monix-tests-single-thread")
    try {
      val latch = new CountDownLatch(2)
      val r = new Runnable { def run() = latch.countDown() }
      s.execute(r)
      s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
      assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
    } finally {
      s.shutdown()
    }
  }

  test("scheduler builder, fixed pool") {
    val s: SchedulerService = Scheduler.fixedPool(name="monix-tests-fixed-pool", poolSize=1)
    try {
      val latch = new CountDownLatch(2)
      val r = new Runnable { def run() = latch.countDown() }
      s.execute(r)
      s.scheduleOnce(10, TimeUnit.MILLISECONDS, r)
      assert(latch.await(15, TimeUnit.MINUTES), "latch.await failed")
    } finally {
      s.shutdown()
    }
  }
}