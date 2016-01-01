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

package monix

import java.util.concurrent.CancellationException
import minitest.TestSuite
import monix.concurrent.Cancelable
import monix.concurrent.schedulers.TestScheduler
import monix.exceptions.DummyException
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object TaskTest extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.get.tasks.isEmpty, "should not have tasks left to execute")
  }

  test("Task.apply should work, lazily") { implicit s =>
    var wasTriggered = false
    def trigger() = { wasTriggered = true; "result" }

    val task = Task(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.apply should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task.apply can be cancelled") { implicit s =>
    var wasTriggered = false
    def trigger() = {
      wasTriggered = true; "result"
    }

    val task = Task(trigger())
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    // cancelling after scheduled for execution, but before execution
    f.cancel()

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assert(f.value.get.failed.get.isInstanceOf[CancellationException], "isInstanceOf[CancellationException]")
  }

  test("Task.create should work") { implicit s =>
    var wasTriggered = false
    def trigger() = { wasTriggered = true; "result" }

    val task = Task.create[String] { (cb, s) => cb.onSuccess(trigger()); Cancelable.empty }
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task.create should protect against user code errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.create[Int] { (cb,s) => throw ex }.runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#runAsync should work") { implicit s =>
    val task = Task(1)
    var received = 0

    task.runAsync.onSuccess {
      case r => received = r
    }

    s.tick()
    assertEquals(received, 1)
  }

  test("Task#runAsync should signal error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex)
    var received: Throwable = null

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#runAsync(Callback) should protect against broken onSuccess and respect the contract") { implicit s =>
    val ex = DummyException("dummy")
    var receivedCount = 0
    val task = Task(1)

    task.runAsync(new Task.Callback[Int] {
      def onSuccess(value: Int) = {
        receivedCount += 1
        throw ex
      }
      def onError(ex: Throwable): Unit = {
        receivedCount += 1
      }
    })

    s.tick()
    assertEquals(receivedCount, 1)
    assertEquals(s.state.get.lastReportedError, ex)
  }

  test("Task#runAsync(Try[T] => Unit) should work") { implicit s =>
    val task = Task(1)
    var received = 0

    task.runAsync(_.foreach(x => received = x))

    s.tick()
    assertEquals(received, 1)
  }

  test("Task#runAsync(Try[T] => Unit) should signal error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex)
    var received: Throwable = null

    task.runAsync(_.failed.foreach(x => received = x))

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }


  test("Task#map should work onSuccess") { implicit s =>
    val task = Task(1 + 1).map(_ * 2)
    val f = task.runAsync

    assert(!f.isCompleted, "f.isCompleted should be false")
    s.tick()

    assertEquals(f.value, Some(Success(4)))
  }

  test("Task#map should work onError") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    val task = Task[Int](throw ex).map(_ * 2)

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#map should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    val task = Task(1).map(x => throw ex)

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#flatten should work") { implicit s =>
    // note, this can trigger stack overflows
    def sum(n: Int): Task[Int] = {
      if (n == 0) Task.now(0) else
        Task(n).map(x => sum(x-1).map(_ + x)).flatten
    }

    val task = sum(100)
    val f = task.runAsync

    assert(!f.isCompleted, "f.isCompleted should be false")
    s.tick()

    assertEquals(f.value, Some(Success(5050)))
  }

  test("Task#flatMap should work") { implicit s =>
    // note, this can trigger stack overflows
    def sum(n: Int): Task[Int] = {
      if (n == 0) Task.now(0) else
        Task(n).flatMap(x => sum(x-1).map(_ + x))
    }

    val task = sum(100)
    val f = task.runAsync

    assert(!f.isCompleted, "f.isCompleted should be false")
    s.tick()

    assertEquals(f.value, Some(Success(5050)))
  }

  test("Task#flatMap should work onError") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    val task = Task[Int](throw ex)
      .flatMap(x => Task.now(x * 2))

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#flatMap should protect against user code") { implicit s =>
    val ex = DummyException("dummy")
    var received: Throwable = null
    val task = Task(1).flatMap(x => throw ex)

    task.runAsync.onFailure {
      case error => received = error
    }

    s.tick()
    assertEquals(received, ex)
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#delay should work") { implicit s =>
    var wasTriggered = false
    def trigger() = { wasTriggered = true; "result" }

    val task = Task(trigger()).delay(1.second)
    assert(!wasTriggered, "!wasTriggered")

    val f = task.runAsync
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick()
    assert(!wasTriggered, "!wasTriggered")
    assertEquals(f.value, None)

    s.tick(1.second)
    assert(wasTriggered, "wasTriggered")
    assertEquals(f.value, Some(Success("result")))
  }

  test("Task#failed should project the failure") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).failed.runAsync

    s.tick()
    assertEquals(f.value, Some(Success(ex)))
    assertEquals(s.state.get.lastReportedError, null)
  }

  test("Task#failed should fail if source is successful") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task(1).failed.runAsync

    s.tick()
    assert(f.value.get.isFailure && f.value.get.failed.get.isInstanceOf[NoSuchElementException],
      "isInstanceOf[NoSuchElementException]")
    assertEquals(s.state.get.lastReportedError, null)
  }
}
