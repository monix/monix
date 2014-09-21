/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
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

import java.util.concurrent.TimeoutException
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import org.scalatest.FunSuite
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}


class AsyncSchedulerTest extends FunSuite {
  val s = Scheduler.Implicits.global

  test("scheduleOnce with delay") {
    val p = Promise[Long]()
    val startedAt = System.nanoTime()
    s.scheduleOnce(100.millis, p.success(System.nanoTime()))

    val timeTaken = Await.result(p.future, 3.second)
    assert((timeTaken - startedAt).nanos.toMillis >= 100)
  }

  test("scheduleOnce with delay lower than 1.milli") {
    val p = Promise[Int]()
    s.scheduleOnce(20.nanos, p.success(1))
    assert(Await.result(p.future, 3.seconds) === 1)
  }

  test("scheduleOnce with delay and cancel") {
    val p = Promise[Int]()
    val task = s.scheduleOnce(100.millis, p.success(1))
    task.cancel()

    intercept[TimeoutException] {
      Await.result(p.future, 150.millis)
    }
  }

  test("schedule periodically") {
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub() = s.scheduleRepeated(10.millis, 50.millis, {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    assert(Await.result(p.future, 5.second) === 4)
  }

  test("schedule recursively") {
    val p = Promise[Int]()
    var value = 0

    s.scheduleRecursive(10.millis, 50.millis, { reschedule =>
      if (value + 1 == 4) {
        value += 1
        p.success(value)
      }
      else if (value < 4) {
        value += 1
        reschedule()
      }
    })

    assert(Await.result(p.future, 5.second) === 4)
  }
}
