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

import minitest.TestSuite
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import scala.concurrent.Promise
import scala.util.Success

object TaskSemaphoreSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  test("simple green-light") { implicit s =>
    val semaphore = TaskSemaphore(maxParallelism = 4)
    val future = semaphore.greenLight(Task(1)).runAsync

    assertEquals(semaphore.activeCount.value, 1)
    assert(!future.isCompleted, "!future.isCompleted")

    s.tick()
    assertEquals(future.value, Some(Success(1)))
    assertEquals(semaphore.activeCount.value, 0)
  }

  test("should release on cancel") { implicit s =>
    val semaphore = TaskSemaphore(maxParallelism = 4)
    val p = Promise[Int]()
    var effect = 0

    val future = semaphore
      .greenLight(Task.defer(Task.fromFuture(p.future).map { _ => effect = 100 }))
      .runAsync

    s.tick()
    assertEquals(semaphore.activeCount.value, 1)
    assertEquals(future.value, None)

    future.cancel(); s.tick()
    assertEquals(semaphore.activeCount.value, 0)
    assertEquals(future.value, None)
    assertEquals(effect, 0)
  }

  testAsync("real async test of many tasks") { _ =>
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

  test("await for release of all active and pending permits") { implicit s =>
    val semaphore = TaskSemaphore(maxParallelism = 2)
    val p1 = semaphore.acquire.runAsync
    assertEquals(p1.value, Some(Success(())))
    val p2 = semaphore.acquire.runAsync
    assertEquals(p2.value, Some(Success(())))

    val p3 = semaphore.acquire.runAsync
    assert(!p3.isCompleted, "!p3.isCompleted")
    val p4 = semaphore.acquire.runAsync
    assert(!p4.isCompleted, "!p4.isCompleted")

    val all1 = semaphore.awaitAllReleased.runAsync
    assert(!all1.isCompleted, "!all1.isCompleted")

    semaphore.release.runAsync; s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release.runAsync; s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release.runAsync; s.tick()
    assert(!all1.isCompleted, "!all1.isCompleted")
    semaphore.release.runAsync; s.tick()
    assert(all1.isCompleted, "all1.isCompleted")

    // REDO
    val p5 = semaphore.acquire.runAsync
    assert(p5.isCompleted, "p5.isCompleted")
    val all2 = semaphore.awaitAllReleased.runAsync
    s.tick(); assert(!all2.isCompleted, "!all2.isCompleted")
    semaphore.release.runAsync; s.tick()
    assert(all2.isCompleted, "all2.isCompleted")

    // Already completed
    val all3 = semaphore.awaitAllReleased.runAsync
    assert(all3.isCompleted, "all3.isCompleted")
  }

  test("acquire is cancelable") { implicit s =>
    val semaphore = TaskSemaphore(maxParallelism = 2)

    val p1 = semaphore.acquire.runAsync
    assert(p1.isCompleted, "p1.isCompleted")
    val p2 = semaphore.acquire.runAsync
    assert(p2.isCompleted, "p2.isCompleted")

    val p3 = semaphore.acquire.runAsync
    assert(!p3.isCompleted, "!p3.isCompleted")
    assertEquals(semaphore.activeCount.value, 2)

    p3.cancel()
    semaphore.release.runAsync
    assertEquals(semaphore.activeCount.value, 1)
    semaphore.release.runAsync
    assertEquals(semaphore.activeCount.value, 0)

    s.tick()
    assertEquals(semaphore.activeCount.value, 0)
    assert(!p3.isCompleted, "!p3.isCompleted")
  }
}
