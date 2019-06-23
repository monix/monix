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

import scala.concurrent.{ExecutionContext, Future}

object TaskToFutureJVMSuite extends SimpleTestSuite {
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

  test("Task.fromFuture should shift back to the main scheduler") { _ =>
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task.fromFuture(Future(1)(s2)).flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.fromFuture(error) should shift back to the main scheduler") { _ =>
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val dummy = DummyException("dummy")
    val task = Task.fromFuture(Future(throw dummy)(s2)).attempt.flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.deferFuture should shift back to the main scheduler") { _ =>
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task.deferFuture(Future(1)(s2)).flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.deferFuture(error) should shift back to the main scheduler") { _ =>
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val dummy = DummyException("dummy")
    val task = Task.deferFuture(Future(throw dummy)(s2)).attempt.flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.deferFutureAction should shift back to the main scheduler") { _ =>
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task.deferFutureAction(implicit s => Future(1)(s2)).flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.deferFutureAction(error) should shift back to the main scheduler") { _ =>
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val dummy = DummyException("dummy")
    val task = Task
      .deferFutureAction(implicit s => Future(throw dummy)(s2))
      .attempt
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.deferFuture(cancelable) should shift back to the main scheduler") { _ =>
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val f = Task.eval(1).runToFuture
    val task = Task.deferFuture(f.map(x => x)(s2)).flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.deferFutureAction(cancelable) should shift back to the main scheduler") { _ =>
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val f = Task.eval(1).delayExecution(1.second).runToFuture(s2)
    val task = Task.deferFutureAction(implicit s => f).flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.fromFuture(completed) should shift back to the main scheduler") { s2 =>
    implicit val s = Scheduler(TestEC)

    val f = Future(1)(s2)
    val task = Task.fromFuture(f).flatMap(_ => Task(Thread.currentThread().getName))

    s2.tick()
    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.deferFuture(completed) should shift back to the main scheduler") { s2 =>
    implicit val s = Scheduler(TestEC)

    val f = Future(1)(s2)
    val task = Task.deferFuture(f).flatMap(_ => Task(Thread.currentThread().getName))

    s2.tick()
    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.deferFutureAction(completed) should shift back to the main scheduler") { s2 =>
    implicit val s = Scheduler(TestEC)

    val f = Future(1)(s2)
    val task = Task.deferFutureAction(_ => f).flatMap(_ => Task(Thread.currentThread().getName))

    s2.tick()
    assertEquals(task.runSyncUnsafe(), ThreadName)
  }
}
