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

package monix.execution.internal.forkJoin

import minitest.SimpleTestSuite


object ForkJoinJVMSuite extends SimpleTestSuite {
  test("execute tasks on saturated forkJoin Scheduler") {
    import monix.execution.Scheduler

    import java.util.concurrent.{CountDownLatch, TimeUnit}
    import scala.concurrent.duration._
    import scala.concurrent.{Await, Promise}

    val maxThreads = 4
    val s: Scheduler = Scheduler.forkJoin(2, maxThreads)

    // make the pool reach its maximum thread count
    val cdl = new CountDownLatch(maxThreads)
    for (_ <- 0 until maxThreads) {
      s.execute(() => scala.concurrent.blocking {
        Thread.sleep(100)
        cdl.countDown()
      })
    }
    assert(cdl.await(5, TimeUnit.SECONDS))

    val p = Promise[Int]()
    s.execute(() => p.success(42))
    assert(Await.result(p.future, 5.seconds) == 42)
  }
}
