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

package monix.execution.misc

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import scala.util.Success

object AsyncQueueSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  test("simple offer and poll") { implicit s =>
    val queue = AsyncQueue.empty[Int]

    queue.offer(1)
    queue.offer(2)
    queue.offer(3)

    assertEquals(queue.poll().value, Some(Success(1)))
    assertEquals(queue.poll().value, Some(Success(2)))
    assertEquals(queue.poll().value, Some(Success(3)))
  }

  test("async poll") { implicit s =>
    val queue = AsyncQueue.empty[Int]

    queue.offer(1)
    assertEquals(queue.poll().value, Some(Success(1)))

    val f = queue.poll()
    assertEquals(f.value, None)

    queue.offer(2); s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("clear") { implicit s =>
    val queue = AsyncQueue.empty[Int]

    queue.offer(1)
    queue.clear()

    val f = queue.poll()
    assertEquals(f.value, None)
  }

  test("clearAndOffer") { implicit s =>
    val queue = AsyncQueue(1,2,3)
    queue.clearAndOffer(4)

    val f = queue.poll()
    assertEquals(f.value, Some(Success(4)))
  }
}
