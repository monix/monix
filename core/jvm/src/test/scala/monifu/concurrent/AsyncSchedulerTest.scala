/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.concurrent

import java.util.concurrent.{CountDownLatch, TimeUnit, TimeoutException}
import minitest.SimpleTestSuite
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.{BooleanCancelable, SingleAssignmentCancelable}
import monifu.internal.concurrent.RunnableAction
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}


object AsyncSchedulerTest extends SimpleTestSuite {
  val s = monifu.concurrent.Implicits.globalScheduler

  def scheduleOnce(s: Scheduler, delay: FiniteDuration)(action: => Unit): Cancelable = {
    s.scheduleOnce(delay.length, delay.unit, RunnableAction(action))
  }
  
  test("scheduleOnce with delay") {
    val p = Promise[Long]()
    val startedAt = System.nanoTime()
    scheduleOnce(s, 100.millis)(p.success(System.nanoTime()))

    val timeTaken = Await.result(p.future, 3.second)
    assert((timeTaken - startedAt).nanos.toMillis >= 100)
  }

  test("scheduleOnce with delay lower than 1.milli") {
    val p = Promise[Int]()
    scheduleOnce(s, 20.nanos)(p.success(1))
    assert(Await.result(p.future, 3.seconds) == 1)
  }

  test("scheduleOnce with delay and cancel") {
    val p = Promise[Int]()
    val task = scheduleOnce(s, 100.millis)(p.success(1))
    task.cancel()

    intercept[TimeoutException] {
      Await.result(p.future, 150.millis)
    }
  }

  test("schedule with fixed delay") {
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub() = s.scheduleWithFixedDelay(10, 50, TimeUnit.MILLISECONDS, RunnableAction {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    assert(Await.result(p.future, 5.second) == 4)
  }

  test("schedule at fixed rate") {
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub() = s.scheduleAtFixedRate(10, 50, TimeUnit.MILLISECONDS, RunnableAction {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    assert(Await.result(p.future, 5.second) == 4)
  }

  test("scheduleOnce simple runnable") {
    val latch = new CountDownLatch(1)
    s.scheduleOnce(RunnableAction {
      latch.countDown()
    })

    assert(latch.await(10, TimeUnit.SECONDS), "latch.await")
  }

  test("scheduleOnce with simple runnable should cancel") {
    val s = Scheduler.singleThread("single-threaded-test")
    val started = new CountDownLatch(1)
    val continue = new CountDownLatch(1)
    val wasTriggered = new CountDownLatch(1)

    s.scheduleOnce(RunnableAction {
      started.countDown()
      // block our thread
      continue.await()
    })

    assert(started.await(10, TimeUnit.SECONDS), "started.await")
    val cancelable = s.scheduleOnce(RunnableAction {
      wasTriggered.countDown()
    })

    cancelable.cancel()
    assert(cancelable.asInstanceOf[BooleanCancelable].isCanceled, "cancelable.isCanceled")
    continue.countDown()

    assert(!wasTriggered.await(100, TimeUnit.MILLISECONDS))
  }
}
