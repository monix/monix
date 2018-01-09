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

package monix.execution.schedulers

import minitest.SimpleTestSuite
import monix.execution.ExecutionModel.{AlwaysAsyncExecution, BatchedExecution, SynchronousExecution}

object ExecutionModelSuite extends SimpleTestSuite {
  test("SynchronousExecution") {
    val em = SynchronousExecution
    assert(em.isSynchronous)
    assert(!em.isAlwaysAsync)
    assert(!em.isBatched)

    assertEquals(em.recommendedBatchSize, 1073741824)
    assertEquals(em.batchedExecutionModulus, 1073741823)

    for (i <- 0 until 100)
      assertEquals(em.nextFrameIndex(i), 1)
  }

  test("AlwaysAsyncExecution") {
    val em = AlwaysAsyncExecution
    assert(em.isAlwaysAsync)
    assert(!em.isSynchronous)
    assert(!em.isBatched)

    assertEquals(em.recommendedBatchSize, 1)
    assertEquals(em.batchedExecutionModulus, 0)

    for (i <- 0 until 100)
      assertEquals(em.nextFrameIndex(i), 0)
  }

  test("BatchedExecution") {
    for (i <- 2 to 512) {
      val em = BatchedExecution(i)
      assert(em.isBatched)
      assert(!em.isAlwaysAsync)
      assert(!em.isSynchronous)

      assert(em.recommendedBatchSize % 2 == 0)
      assertEquals(em.batchedExecutionModulus, em.recommendedBatchSize - 1)
      assert(em.recommendedBatchSize >= i)

      var index = 1
      for (j <- 1 until em.recommendedBatchSize * 3) {
        index = em.nextFrameIndex(index)
        assert(index >= 0 && index < em.recommendedBatchSize)
      }
    }
  }
}
