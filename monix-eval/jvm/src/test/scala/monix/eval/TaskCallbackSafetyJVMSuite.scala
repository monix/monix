/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.eval

import java.util.concurrent.{CountDownLatch, TimeUnit}

import minitest.SimpleTestSuite
import monix.execution.CallbackSafetyJVMSuite.{WORKERS, assert, await}
import monix.execution.schedulers.SchedulerService
import monix.execution.{Callback, Scheduler}

import scala.concurrent.duration._

object TaskCallbackSafetyJVMSuite extends SimpleTestSuite {
  val WORKERS = 10
  val RETRIES = 1000

  test("Task.async has a safe callback") {
    runTest(Task.async)
  }

  test("Task.async0 has a safe callback") {
    runTest(f => Task.async0((_, cb) => f(cb)))
  }

  test("Task.asyncF has a safe callback") {
    runTest(f => Task.asyncF(cb => Task(f(cb))))
  }

  test("Task.cancelable has a safe callback") {
    runTest(f => Task.cancelable { cb => f(cb); Task(()) })
  }

  test("Task.cancelable0 has a safe callback") {
    runTest(f => Task.cancelable0 { (_, cb) => f(cb); Task(()) })
  }

  def runTest(create: (Callback[Throwable, Int] => Unit) => Task[Int]): Unit = {
    implicit val sc: SchedulerService = Scheduler.io("task-callback-safety")
    try {
      for (_ <- 0 until RETRIES) {
        val task = create { cb => runConcurrently(sc)(cb.tryOnSuccess(1)) }
        val latch = new CountDownLatch(1)
        var effect = 0

        task.runAsync {
          case Right(a) =>
            effect += a
            latch.countDown()
          case Left(_) =>
            latch.countDown()
        }

        await(latch)
        assertEquals(effect, 1)
      }
    } finally {
      sc.shutdown()
      assert(sc.awaitTermination(10.seconds), "io.awaitTermination")
    }
  }

  def runConcurrently(sc: Scheduler)(f: => Unit): Unit = {
    val latchWorkersStart = new CountDownLatch(WORKERS)
    val latchWorkersFinished = new CountDownLatch(WORKERS)

    for (_ <- 0 until WORKERS) {
      sc.executeAsync{ () =>
        latchWorkersStart.countDown()
        try {
          f
        } finally {
          latchWorkersFinished.countDown()
        }
      }
    }

    await(latchWorkersStart)
    await(latchWorkersFinished)
  }

  def await(latch: CountDownLatch): Unit = {
    val seconds = 10
    assert(latch.await(seconds, TimeUnit.SECONDS), s"latch.await($seconds seconds)")
  }
}
