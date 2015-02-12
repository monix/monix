/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.concurrent

import org.scalatest.FunSuite
import scala.concurrent.{Promise, Await, Future}
import concurrent.duration._
import java.util.concurrent.TimeoutException
import monifu.concurrent.extensions._
import monifu.concurrent.Implicits.globalScheduler


class ExtensionsTest extends FunSuite {
  test("delayedResult") {
    val startedAt = System.nanoTime()
    val f = Future.delayedResult(100.millis)("TICK")

    assert(Await.result(f, 10.seconds) === "TICK")
    assert((System.nanoTime() - startedAt).nanos >= 100.millis)
  }

  test("withTimeout should succeed") {
    val f = Future.delayedResult(50.millis)("Hello world!")
    val t = f.withTimeout(300.millis)

    assert(Await.result(t, 10.seconds) === "Hello world!")
  }

  test("withTimeout should fail") {
    val f = Future.delayedResult(1.second)("Hello world!")
    val t = f.withTimeout(30.millis)

    intercept[TimeoutException] {
      Await.result(t, 10.seconds)
    }
  }

  test("ensureDuration should succeed on lower time bound") {
    val start = System.nanoTime()
    val f = Future(1).withMinDuration(400.millis)
    assert(Await.result(f, 10.seconds) === 1)

    val duration = (System.nanoTime() - start).nanos
    assert(duration >= 400.millis, s"$duration < 400 millis")
  }

  test("ensureDuration should succeed on lower bound, with upper bound specified") {
    val start = System.nanoTime()
    val f = Future(1).withMinDuration(400.millis)
    assert(Await.result(f, 10.seconds) === 1)

    val duration = (System.nanoTime() - start).nanos
    assert(duration >= 400.millis, s"$duration >= 400 millis")
  }

  test("execute(callback) should work") {
    val p = Promise[Int]()
    globalScheduler.scheduleOnce {
      p.success(1)
    }

    assert(Await.result(p.future, 5.seconds) === 1)
  }
}
