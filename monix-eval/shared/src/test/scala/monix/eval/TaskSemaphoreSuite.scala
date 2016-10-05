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

package monix.eval

import minitest.TestSuite
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler

import scala.concurrent.Promise
import scala.util.Success

object TaskSemaphoreSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.get.tasks.isEmpty, "should not have tasks left to execute")

  test("simple green-light") { implicit s =>
    val semaphore = TaskSemaphore(maxParallelism = 4)
    val future = semaphore.greenLight(Task(1)).runAsync

    assertEquals(semaphore.activeCount, 1)
    assert(!future.isCompleted, "!future.isCompleted")

    s.tick()
    assertEquals(future.value, Some(Success(1)))
    assertEquals(semaphore.activeCount, 0)
  }

  test("should release on cancel") { implicit s =>
    val semaphore = TaskSemaphore(maxParallelism = 4)
    val p = Promise[Int]()
    var effect = 0

    val future = semaphore
      .greenLight(Task.defer(Task.fromFuture(p.future).map { _ => effect = 100 }))
      .runAsync

    s.tick()
    assertEquals(semaphore.activeCount, 1)
    assertEquals(future.value, None)

    future.cancel(); s.tick()
    assertEquals(semaphore.activeCount, 0)
    assertEquals(future.value, None)
    assertEquals(effect, 0)
  }

  test("real async test of many tasks") { _ =>
    // Executing tasks on the global scheduler!
    import monix.execution.Scheduler.Implicits.global
    val semaphore = TaskSemaphore(maxParallelism = 4)
    val count = if (Platform.isJVM) 100000 else 1000

    val tasks = for (i <- 0 until count) yield
      semaphore.greenLight(Task(i))
    val sum =
      Task.gatherUnordered(tasks).map(_.sum)

    // Asynchronous result, to be handled by Minitest
    for (result <- sum.runAsync) yield {
      assertEquals(result, count * (count - 1) / 2)
    }
  }
}
