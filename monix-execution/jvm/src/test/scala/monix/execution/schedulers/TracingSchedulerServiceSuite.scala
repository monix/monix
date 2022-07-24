/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import java.util.concurrent.TimeUnit

import monix.execution.BaseTestSuite
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.Scheduler
import monix.execution.misc.Local

import scala.concurrent.Future
import scala.util.control.NonFatal

class TracingSchedulerServiceSuite extends BaseTestSuite {
  test("captures locals in actual async execution") {
    val service = TracingSchedulerService(Scheduler.singleThread("test"))
    val f1 = {
      implicit val ec = service
      val local1 = Local(0)
      val local2 = Local(0)
      local2 := 100

      val ref = Local.isolate {
        local1 := 100
        Future(local1.get + local2.get)
      }
      local1 := 999
      local2 := 999
      ref
    }

    import Scheduler.Implicits.global
    val f2 = service.awaitTermination(100, TimeUnit.HOURS, global)

    val ff = f1.map { r =>
      try {
        assert(!service.isShutdown, "!service.isShutdown")
        assert(!service.isTerminated)
        assertEquals(r, 200)
        service.shutdown()
      } catch {
        case NonFatal(e) if !service.isShutdown =>
          service.shutdown()
          throw e
      }
    }

    for (_ <- ff; _ <- f2) yield {
      assert(service.isTerminated, "service.isTerminated")
      assert(service.isShutdown, "service.isShutdown")
    }
  }

  test("executionModel") {
    val ec: SchedulerService = Scheduler.singleThread("test")
    val traced = TracingSchedulerService(ec)
    try {
      assertEquals(traced.executionModel, ec.executionModel)
      val traced2 = traced.withExecutionModel(AlwaysAsyncExecution)
      assertEquals(traced2.executionModel, AlwaysAsyncExecution)
    } finally {
      traced.shutdown()
    }
  }
}
