/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import minitest.SimpleTestSuite
import monix.execution.Scheduler
import monix.execution.exceptions.DummyException

import scala.concurrent.ExecutionContext

object TaskAsyncJVMSuite extends SimpleTestSuite {
  private val ThreadName = "test-thread"

  private val TestEC = new ExecutionContext {
    def execute(r: Runnable): Unit = {
      val th = new Thread(r)
      th.setName(ThreadName)
      th.start()
    }

    def reportFailure(cause: Throwable): Unit =
      throw cause
  }

  test("Task.async should shift back to the main scheduler on success") {
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .async[Int] { cb =>
        s2.executeAsync { () =>
          cb.onSuccess(1)
        }
      }
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.async should shift back to the main scheduler on error") {
    val e = DummyException("dummy")

    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .async[Int] { cb =>
        s2.executeAsync { () =>
          cb.onError(e)
        }
      }
      .attempt
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.async0 should shift back to the main scheduler on success") {
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .async0[Int] { (_, cb) =>
        s2.executeAsync { () =>
          cb.onSuccess(1)
        }
      }
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.async0 should shift back to the main scheduler on error") {
    val e = DummyException("dummy")

    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .async0[Int] { (_, cb) =>
        s2.executeAsync { () =>
          cb.onError(e)
        }
      }
      .attempt
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.asyncF should shift back to the main scheduler on success") {
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .asyncF[Int] { cb =>
        Task.delay {
          s2.executeAsync { () =>
            cb.onSuccess(1)
          }
        }
      }
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.asyncF should shift back to the main scheduler on error") {
    val e = DummyException("dummy")

    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .asyncF[Int] { cb =>
        Task.delay {
          s2.executeAsync { () =>
            cb.onError(e)
          }
        }
      }
      .attempt
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }
}
