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

package monix.eval

import monix.execution.Scheduler

import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.duration._
import scala.util.Try

object TaskFromTrySuite extends BaseTestSuite {



  test("Task.fromTry should be lazy evaluated") { implicit s =>
    val t = Task.fromTry(Try{
      Thread.sleep(60000L) // Simulates a heavy computation that blocks the thread (e.g. I/O, interaction with blocking Java libraries calls, etc.)
      10
    }).timeout(5.second)
    intercept[TimeoutException]{
      Await.result(t.runAsync(Scheduler.io()), 10.seconds)
      fail("Task should have raised a TimeoutException due lazy evaluation of the fromTry")
    }
  }
}
