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

package monix.execution

import minitest.SimpleTestSuite
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.Success

object CancelableFutureJVMSuite extends SimpleTestSuite {
  test("async.result") {
    val f = CancelableFuture(Future(1), Cancelable.empty)
    val r = Await.result(f, Duration.Inf)
    assertEquals(r, 1)
  }

  test("async.ready") {
    val f = CancelableFuture(Future(1), Cancelable.empty)
    Await.ready(f, Duration.Inf)
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.result") {
    val f = CancelableFuture.successful(1)
    val r = Await.result(f, Duration.Inf)
    assertEquals(r, 1)
  }

  test("now.ready") {
    val f = CancelableFuture.successful(1)
    Await.ready(f, Duration.Inf)
    assertEquals(f.value, Some(Success(1)))
  }

  test("never") {
    val f = CancelableFuture.never[Int]
    intercept[TimeoutException] { Await.result(f, 1.milli) }
    intercept[TimeoutException] { Await.ready(f, 1.milli) }
  }
}
