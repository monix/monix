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

package monix.eval

import java.util.concurrent.{CountDownLatch, TimeUnit}

import minitest.SimpleTestSuite
import monix.execution.exceptions.{CallbackCalledMultipleTimesException, DummyException}
import monix.execution.schedulers.SchedulerService
import monix.execution.{Callback, Scheduler, TestUtils}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TaskCallbackSafetyJVMSuite extends SimpleTestSuite with TestUtils {
  val WORKERS = 10
  val RETRIES = if (!isCI) 1000 else 100

  test("Task.async has a safe callback") {
    runConcurrentCallbackTest(Task.async)
  }

  test("Task.async0 has a safe callback") {
    runConcurrentCallbackTest(f => Task.async0((_, cb) => f(cb)))
  }

  test("Task.asyncF has a safe callback") {
    runConcurrentCallbackTest(f => Task.asyncF(cb => Task(f(cb))))
  }

  test("Task.cancelable has a safe callback") {
    runConcurrentCallbackTest(f =>
      Task.cancelable { cb =>
        f(cb); Task(())
      })
  }

  test("Task.cancelable0 has a safe callback") {
    runConcurrentCallbackTest(f =>
      Task.cancelable0 { (_, cb) =>
        f(cb); Task(())
      })
  }

  def runConcurrentCallbackTest(create: (Callback[Throwable, Int] => Unit) => Task[Int]): Unit = {
    def run(trigger: Callback[Throwable, Int] => Unit): Unit = {
      implicit val sc: SchedulerService = Scheduler.io("task-callback-safety")
      try {
        for (_ <- 0 until RETRIES) {
          val task = create { cb =>
            runConcurrently(sc)(trigger(cb))
          }
          val latch = new CountDownLatch(1)
          var effect = 0

          task.runAsync {
            case Right(_) =>
              effect += 1
              latch.countDown()
            case Left(_) =>
              effect += 1
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

    run { cb => cb.tryOnSuccess(1); () }
    run { cb => cb.tryApply(Right(1)); () }
    run { cb => cb.tryApply(Success(1)); () }

    run(cb =>
      try cb.onSuccess(1)
      catch { case _: CallbackCalledMultipleTimesException => () })
    run(cb =>
      try cb(Right(1))
      catch { case _: CallbackCalledMultipleTimesException => () })
    run(cb =>
      try cb(Success(1))
      catch { case _: CallbackCalledMultipleTimesException => () })

    val dummy = DummyException("dummy")

    run { cb => cb.tryOnError(dummy); () }
    run { cb => cb.tryApply(Left(dummy)); () }
    run { cb => cb.tryApply(Failure(dummy)); () }

    run(cb =>
      try cb.onError(dummy)
      catch { case _: CallbackCalledMultipleTimesException => () })
    run(cb =>
      try cb(Left(dummy))
      catch { case _: CallbackCalledMultipleTimesException => () })
    run(cb =>
      try cb(Failure(dummy))
      catch { case _: CallbackCalledMultipleTimesException => () })
  }

  def runConcurrently(sc: Scheduler)(f: => Unit): Unit = {
    val latchWorkersStart = new CountDownLatch(WORKERS)
    val latchWorkersFinished = new CountDownLatch(WORKERS)

    for (_ <- 0 until WORKERS) {
      sc.execute { () =>
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
    assert(latch.await(seconds.toLong, TimeUnit.SECONDS), s"latch.await($seconds seconds)")
  }
}
