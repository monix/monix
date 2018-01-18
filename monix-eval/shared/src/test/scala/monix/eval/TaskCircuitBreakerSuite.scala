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

import monix.execution.exceptions.{DummyException, ExecutionRejectedException}

import concurrent.duration._
import scala.util.{Failure, Success}

object TaskCircuitBreakerSuite extends BaseTestSuite {
  test("should work for successful async tasks") { implicit s =>
    val Right(circuitBreaker) = TaskCircuitBreaker(
      maxFailures = 5,
      resetTimeout = 1.minute
    ).runSyncMaybe

    var effect = 0
    val task = circuitBreaker.protect(Task {
      effect += 1
    })

    for (i <- 0 until 10000) task.runAsync
    s.tick()
    assertEquals(effect, 10000)
  }

  test("should work for successful immediate tasks") { implicit s =>
    val Right(circuitBreaker) = TaskCircuitBreaker(
      maxFailures = 5,
      resetTimeout = 1.minute
    ).runSyncMaybe

    var effect = 0
    val task = circuitBreaker.protect(Task.eval {
      effect += 1
    })

    for (i <- 0 until 10000) task.runAsync
    assertEquals(effect, 10000)
  }

  test("should be stack safe for successful async tasks (flatMap)") { implicit s =>
    val Right(circuitBreaker) = TaskCircuitBreaker(
      maxFailures = 5,
      resetTimeout = 1.minute
    ).runSyncMaybe

    def loop(n: Int, acc: Int): Task[Int] = {
      if (n > 0)
        circuitBreaker.protect(Task(acc+1))
          .flatMap(s => loop(n-1, s))
      else
        Task.now(acc)
    }

    val f = loop(100000, 0).runAsync; s.tick()
    assertEquals(f.value, Some(Success(100000)))
  }

  test("should be stack safe for successful async tasks (defer)") { implicit s =>
    val Right(circuitBreaker) = TaskCircuitBreaker(
      maxFailures = 5,
      resetTimeout = 1.minute
    ).runSyncMaybe

    def loop(n: Int, acc: Int): Task[Int] =
      Task.defer {
        if (n > 0)
          circuitBreaker.protect(loop(n-1, acc+1))
        else
          Task.now(acc)
      }.executeAsync

    val f = loop(100000, 0).runAsync; s.tick()
    assertEquals(f.value, Some(Success(100000)))
  }

  test("should be stack safe for successful immediate tasks (flatMap)") { implicit s =>
    val Right(circuitBreaker) = TaskCircuitBreaker(
      maxFailures = 5,
      resetTimeout = 1.minute
    ).runSyncMaybe

    def loop(n: Int, acc: Int): Task[Int] = {
      if (n > 0)
        circuitBreaker.protect(Task.eval(acc+1))
          .flatMap(s => loop(n-1, s))
      else
        Task.now(acc)
    }

    val f = loop(100000, 0).runAsync; s.tick()
    assertEquals(f.value, Some(Success(100000)))
  }

  test("should be stack safe for successful immediate tasks (defer)") { implicit s =>
    val Right(circuitBreaker) = TaskCircuitBreaker(
      maxFailures = 5,
      resetTimeout = 1.minute
    ).runSyncMaybe

    def loop(n: Int, acc: Int): Task[Int] =
      Task.defer {
        if (n > 0)
          circuitBreaker.protect(loop(n-1, acc+1))
        else
          Task.now(acc)
      }

    val f = loop(100000, 0).runAsync; s.tick()
    assertEquals(f.value, Some(Success(100000)))
  }

  test("complete workflow with failures and exponential backoff") { implicit s =>
    var openedCount = 0
    var closedCount = 0
    var halfOpenCount = 0
    var rejectedCount = 0

    val circuitBreaker = {
      val Right(cb) = TaskCircuitBreaker(
        maxFailures = 5,
        resetTimeout = 1.minute,
        exponentialBackoffFactor = 2,
        maxResetTimeout = 10.minutes
      ).runSyncMaybe

      cb.doOnOpen(Task.eval { openedCount += 1})
        .doOnClosed(Task.eval { closedCount += 1 })
        .doOnHalfOpen(Task.eval { halfOpenCount += 1 })
        .doOnRejectedTask(Task.eval { rejectedCount += 1 })
    }

    val dummy = DummyException("dummy")
    val taskInError = circuitBreaker.protect(Task.eval[Int](throw dummy))
    val taskSuccess = circuitBreaker.protect(Task.eval { 1 })

    assertEquals(taskInError.runAsync.value, Some(Failure(dummy)))
    assertEquals(taskInError.runAsync.value, Some(Failure(dummy)))
    assertEquals(circuitBreaker.state, TaskCircuitBreaker.Closed(2))

    // A successful value should reset the counter
    assertEquals(taskSuccess.runAsync.value, Some(Success(1)))
    assertEquals(circuitBreaker.state, TaskCircuitBreaker.Closed(0))

    assertEquals(taskInError.runAsync.value, Some(Failure(dummy)))
    assertEquals(taskInError.runAsync.value, Some(Failure(dummy)))
    assertEquals(taskInError.runAsync.value, Some(Failure(dummy)))
    assertEquals(taskInError.runAsync.value, Some(Failure(dummy)))
    assertEquals(circuitBreaker.state, TaskCircuitBreaker.Closed(4))

    assertEquals(taskInError.runAsync.value, Some(Failure(dummy)))
    assertEquals(circuitBreaker.state, TaskCircuitBreaker.Open(
      startedAt = s.currentTimeMillis(),
      resetTimeout = 1.minute
    ))

    // Getting rejections from now on, testing reset timeout
    var resetTimeout = 60.seconds
    for (i <- 0 until 30) {
      val now = s.currentTimeMillis()
      val nextTimeout = {
        val value = resetTimeout * 2
        if (value > 10.minutes) 10.minutes else value
      }

      intercept[ExecutionRejectedException](taskInError.runAsync.value.get.get)
      s.tick(resetTimeout - 1.second)
      intercept[ExecutionRejectedException](taskInError.runAsync.value.get.get)

      // After 1 minute we should attempt a reset
      s.tick(1.second)
      assertEquals(circuitBreaker.state, TaskCircuitBreaker.Open(now, resetTimeout))

      // Starting the HalfOpen state
      val delayedTask = circuitBreaker.protect(Task.raiseError(dummy).delayExecution(1.second))
      val delayedResult = delayedTask.runAsync

      assertEquals(circuitBreaker.state,
        TaskCircuitBreaker.HalfOpen(resetTimeout = resetTimeout))

      // Rejecting all other tasks
      intercept[ExecutionRejectedException](taskInError.runAsync.value.get.get)
      intercept[ExecutionRejectedException](taskInError.runAsync.value.get.get)

      // Should migrate back into Open
      s.tick(1.second)
      assertEquals(delayedResult.value, Some(Failure(dummy)))
      assertEquals(circuitBreaker.state, TaskCircuitBreaker.Open(
        startedAt = s.currentTimeMillis(),
        resetTimeout = nextTimeout
      ))

      intercept[ExecutionRejectedException](taskInError.runAsync.value.get.get)

      // Calculate next reset timeout
      resetTimeout = nextTimeout
    }

    // Going back into Closed
    s.tick(resetTimeout)

    val delayedTask = circuitBreaker.protect(Task(1).delayExecution(1.second))
    val delayedResult = delayedTask.runAsync

    assertEquals(circuitBreaker.state, TaskCircuitBreaker.HalfOpen(resetTimeout = resetTimeout))
    intercept[ExecutionRejectedException](taskInError.runAsync.value.get.get)

    s.tick(1.second)
    assertEquals(delayedResult.value, Some(Success(1)))
    assertEquals(circuitBreaker.state, TaskCircuitBreaker.Closed(0))

    assertEquals(rejectedCount, 5 * 30 + 1)
    assertEquals(openedCount, 30 + 1)
    assertEquals(halfOpenCount, 30 + 1)
    assertEquals(closedCount, 1)
  }

  test("validate parameters") { implicit s =>
    intercept[IllegalArgumentException] {
      // Positive maxFailures
      val circuitBreaker = TaskCircuitBreaker(
        maxFailures = -1,
        resetTimeout = 1.minute
      ).runSyncMaybe
    }

    intercept[IllegalArgumentException] {
      // Strictly positive resetTimeout
      val circuitBreaker = TaskCircuitBreaker(
        maxFailures = 2,
        resetTimeout = -1.minute
      ).runSyncMaybe
    }

    intercept[IllegalArgumentException] {
      // exponentialBackoffFactor >= 1
      val circuitBreaker = TaskCircuitBreaker(
        maxFailures = 2,
        resetTimeout = 1.minute,
        exponentialBackoffFactor = 0.5
      ).runSyncMaybe
    }

    intercept[IllegalArgumentException] {
      // Strictly positive maxResetTimeout
      val circuitBreaker = TaskCircuitBreaker(
        maxFailures = 2,
        resetTimeout = 1.minute,
        exponentialBackoffFactor = 2,
        maxResetTimeout = Duration.Zero
      ).runSyncMaybe
    }
  }
}
