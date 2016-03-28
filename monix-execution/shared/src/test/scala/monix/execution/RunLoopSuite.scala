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

package monix.execution

import minitest.SimpleTestSuite
import monix.execution.schedulers.{ExecutionModel, TestScheduler}
import scala.util.control.NonFatal

object RunLoopSuite extends SimpleTestSuite {
  test("RunLoop.start should work") {
    implicit val s = TestScheduler()
    assert(!RunLoop.isAlwaysAsync, "!isAlwaysAsync")

    var triggered = 0

    RunLoop.start { frameId =>
      try {
        triggered += 1
      } catch {
        case NonFatal(ex) =>
          triggered = -1
      }
    }

    assertEquals(triggered, 1)
  }

  test("RunLoop.step should jump threads on barriers") {
    implicit val s = TestScheduler()
    assert(!RunLoop.isAlwaysAsync, "!isAlwaysAsync")

    var triggered = 0
    val barrier = s.executionModel.batchedExecutionModulus

    RunLoop.step(1) { frameId =>
      try {
        triggered += 1
      } catch {
        case NonFatal(ex) =>
          triggered = -1
      }
    }

    assertEquals(triggered, 1)

    RunLoop.step(barrier-1) { frameId =>
      try {
        triggered += 1
      } catch {
        case NonFatal(ex) =>
          triggered = -1
      }
    }

    assertEquals(triggered, 2)

    var lastFrameId = -1
    RunLoop.step(barrier) { frameId =>
      try {
        lastFrameId = frameId
        triggered += 1
      } catch {
        case NonFatal(ex) =>
          triggered = -1
      }
    }

    assertEquals(triggered, 2)
    assertEquals(lastFrameId, -1)
    s.tick()
    assertEquals(triggered, 3)
    assertEquals(lastFrameId, 0)
  }

  test("RunLoop should always execute async if recommendedBatchSize == 1") {
    implicit val s = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    assert(RunLoop.isAlwaysAsync, "isAlwaysAsync")

    var triggered = 0

    RunLoop.start(_ => triggered += 1)
    assertEquals(triggered, 0)

    RunLoop.step(1) { _ => triggered += 1 }
    assertEquals(triggered, 0)

    RunLoop.step(2) { _ => triggered += 1 }
    assertEquals(triggered, 0)

    s.tick()
    assertEquals(triggered, 3)
  }

  test("RunLoop.startAsync should work") {
    implicit val s = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    assert(RunLoop.isAlwaysAsync, "isAlwaysAsync")

    var triggered = 0
    var frameAcc = 0

    RunLoop.startAsync { id => triggered += 1; frameAcc += id }
    RunLoop.startAsync { id => triggered += 1; frameAcc += id }
    RunLoop.startAsync { id => triggered += 1; frameAcc += id }

    assertEquals(triggered, 0)
    assertEquals(frameAcc, 0)

    s.tick()
    assertEquals(triggered, 3)
    assertEquals(frameAcc, 0)
  }
}
