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

package monix.execution.internal.collection

import minitest.SimpleTestSuite

object ArrayQueueSuite extends SimpleTestSuite {
  test("unbounded") {
    val q = ArrayQueue.unbounded[Int]

    for (i <- 0 until 100) q.offer(1)
    assertEquals(q.length, 100)

    var sum = 0
    for (_ <- 0 until 50) sum += q.poll()
    assertEquals(q.length, 50)
    assertEquals(sum, 50)

    for (_ <- 0 until 50) sum += q.poll()
    assertEquals(q.length, 0)
    assertEquals(sum, 100)

    assertEquals(q.isAtCapacity, false)
    assertEquals(q.isEmpty, true)
  }
}
