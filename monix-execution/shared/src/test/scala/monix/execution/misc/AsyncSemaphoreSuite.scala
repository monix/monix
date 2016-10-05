/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.execution.misc

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import monix.execution.internal.Platform
import scala.concurrent.{Future, Promise}
import scala.util.Success

object AsyncSemaphoreSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.get.tasks.isEmpty, "should not have tasks left to execute")

  test("simple greenLight") { implicit s =>
    val semaphore = AsyncSemaphore(maxParallelism = 4)
    val future = semaphore.greenLight(() => Future(100))
    assertEquals(semaphore.activeCount, 1)
    assert(!future.isCompleted, "!future.isCompleted")

    s.tick()
    assertEquals(future.value, Some(Success(100)))
    assertEquals(semaphore.activeCount, 0)
  }

  test("should back-pressure when full") { implicit s =>
    val semaphore = AsyncSemaphore(maxParallelism = 2)

    val p1 = Promise[Int]()
    val f1 = semaphore.greenLight(() => p1.future)
    val p2 = Promise[Int]()
    val f2 = semaphore.greenLight(() => p2.future)

    s.tick()
    assertEquals(semaphore.activeCount, 2)

    val f3 = semaphore.greenLight(() => Future(3))

    s.tick()
    assertEquals(f3.value, None)
    assertEquals(semaphore.activeCount, 2)

    p1.success(1); s.tick()
    assertEquals(semaphore.activeCount, 1)
    assertEquals(f1.value, Some(Success(1)))
    assertEquals(f3.value, Some(Success(3)))

    p2.success(2); s.tick()
    assertEquals(f2.value, Some(Success(2)))
    assertEquals(semaphore.activeCount, 0)
  }

  test("real async test of many futures") { _ =>
    // Executing Futures on the global scheduler!
    import monix.execution.Scheduler.Implicits.global
    val semaphore = AsyncSemaphore(maxParallelism = 4)
    val count = if (Platform.isJVM) 100000 else 1000

    val futures = for (i <- 0 until count) yield
      semaphore.greenLight(() => Future(i))
    val sum =
      Future.sequence(futures).map(_.sum)

    // Asynchronous result, to be handled by Minitest
    for (result <- sum) yield {
      assertEquals(result, count * (count - 1) / 2)
    }
  }
}
