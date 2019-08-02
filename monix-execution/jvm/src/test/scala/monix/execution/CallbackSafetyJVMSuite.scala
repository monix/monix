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

package monix.execution

import java.util.concurrent.{CountDownLatch, TimeUnit}

import minitest.TestSuite
import minitest.api.{AssertionException, MiniTestException}
import monix.execution.exceptions.{CallbackCalledMultipleTimesException, DummyException}
import monix.execution.schedulers.SchedulerService

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object CallbackSafetyJVMSuite extends TestSuite[SchedulerService] {
  val WORKERS = 10
  val RETRIES = 1000

  val isTravis = {
    System.getenv("TRAVIS") == "true" || System.getenv("CI") == "true"
  }

  override def setup(): SchedulerService =
    Scheduler.io("test-callback")

  override def tearDown(env: SchedulerService): Unit = {
    env.shutdown()
    env.awaitTermination(10.seconds)
  }

  test("Callback.safe is thread-safe onSuccess") { implicit sc =>
    executeOnSuccessTest(Callback[Throwable].safe)
  }

  test("Callback.safe is thread-safe onError") { implicit sc =>
    executeOnErrorTest(Callback[Throwable].safe)
  }

  test("Callback.trampolined is thread-safe onSuccess") { implicit sc =>
    executeOnSuccessTest(Callback[Throwable].trampolined)
  }

  test("Callback.trampolined is thread-safe onError") { implicit sc =>
    executeOnErrorTest(Callback[Throwable].trampolined)
  }

  test("Callback.forked is thread-safe onSuccess") { implicit sc =>
    executeOnSuccessTest(Callback[Throwable].forked, isForked = true)
  }

  test("Callback.forked is thread-safe onError") { implicit sc =>
    executeOnErrorTest(Callback[Throwable].forked, isForked = true)
  }

  test("Normal callback is not thread-safe via onSuccess") { implicit sc =>
    intercept[AssertionException] {
      executeOnSuccessTest(x => x)
    }
  }

  test("Normal callback is not thread-safe via onError") { implicit sc =>
    intercept[AssertionException] {
      executeOnErrorTest(x => x)
    }
  }

  test("Callback.fromAttempt is not thread-safe via onSuccess") { implicit sc =>
    if (isTravis) ignore()

    intercept[AssertionException] {
      executeOnSuccessTest({ cb =>
        val f = (r: Either[Throwable, Int]) => cb(r)
        Callback.fromAttempt(f)
      }, retries = RETRIES * 10)
    }
  }

  test("Callback.fromAttempt is not thread-safe via onError") { implicit sc =>
    if (isTravis) ignore()

    intercept[AssertionException] {
      executeOnErrorTest({ cb =>
        val f = (r: Either[Throwable, String]) => cb(r)
        Callback.fromAttempt(f)
      }, retries = RETRIES * 10)
    }
  }

  test("Callback.fromTry is not thread-safe via onSuccess") { implicit sc =>
    if (isTravis) ignore()

    intercept[AssertionException] {
      executeOnSuccessTest({ cb =>
        val f = (r: Try[Int]) => cb(r)
        Callback.fromTry(f)
      }, retries = RETRIES * 10)
    }
  }

  test("Callback.fromTry is not thread-safe via onError") { implicit sc =>
    if (isTravis) ignore()

    intercept[AssertionException] {
      executeOnErrorTest({ cb =>
        val f = (r: Try[String]) => cb(r)
        Callback.fromTry(f)
      }, retries = RETRIES * 10)
    }
  }

  test("Callback.fromAttempt is quasi-safe via onSuccess") { implicit sc =>
    executeQuasiSafeOnSuccessTest { cb =>
      val f = (r: Either[Throwable, Int]) => cb(r)
      Callback.fromAttempt(f)
    }
  }

  test("Callback.fromAttempt is quasi-safe via onError") { implicit sc =>
    executeQuasiSafeOnFailureTest { cb =>
      val f = (r: Either[Throwable, Int]) => cb(r)
      Callback.fromAttempt(f)
    }
  }

  test("Callback.fromTry is quasi-safe via onSuccess") { implicit sc =>
    executeQuasiSafeOnSuccessTest { cb =>
      val f = (r: Try[Int]) => cb(r)
      Callback.fromTry(f)
    }
  }

  test("Callback.fromTry is quasi-safe via onError") { implicit sc =>
    executeQuasiSafeOnFailureTest { cb =>
      val f = (r: Try[Int]) => cb(r)
      Callback.fromTry(f)
    }
  }

  test("Normal callback is not quasi-safe via onSuccess") { implicit sc =>
    intercept[MiniTestException] {
      executeQuasiSafeOnSuccessTest(x => x)
    }
  }

  test("Normal callback is not quasi-safe via onError") { implicit sc =>
    intercept[MiniTestException] {
      executeQuasiSafeOnFailureTest(x => x)
    }
  }

  def executeQuasiSafeOnSuccessTest(wrap: Callback[Throwable, Int] => Callback[Throwable, Int]): Unit = {
    var effect = 0
    val cb = wrap(new Callback[Throwable, Int] {
      override def onSuccess(value: Int): Unit =
        effect += value
      override def onError(e: Throwable): Unit =
        ()
    })

    assert(cb.tryOnSuccess(1), "cb.tryOnSuccess(1)")
    assert(!cb.tryOnSuccess(1), "!cb.tryOnSuccess(1)")

    intercept[CallbackCalledMultipleTimesException] {
      cb.onSuccess(1)
    }
    assertEquals(effect, 1)
  }

  def executeQuasiSafeOnFailureTest(wrap: Callback[Throwable, Int] => Callback[Throwable, Int]): Unit = {
    var effect = 0
    val cb = wrap(new Callback[Throwable, Int] {
      override def onSuccess(value: Int): Unit =
        ()
      override def onError(e: Throwable): Unit =
        effect += 1
    })

    val dummy = DummyException("dummy")
    assert(cb.tryOnError(dummy), "cb.tryOnError(1)")
    assert(!cb.tryOnError(dummy), "!cb.tryOnError(1)")

    intercept[CallbackCalledMultipleTimesException] {
      cb.onError(dummy)
    }
    assertEquals(effect, 1)
  }

  def executeOnSuccessTest(
    wrap: Callback[Throwable, Int] => Callback[Throwable, Int],
    isForked: Boolean = false,
    retries: Int = RETRIES)(implicit sc: Scheduler): Unit = {

    for (_ <- 0 until retries) {
      var effect = 0
      val awaitCallbacks = if (isForked) new CountDownLatch(1) else null

      val cb = wrap(new Callback[Throwable, Int] {
        override def onSuccess(value: Int): Unit = {
          effect += value
          if (isForked) {
            awaitCallbacks.countDown()
          }
        }
        override def onError(e: Throwable): Unit =
          throw e
      })

      runConcurrently(sc)(cb.tryOnSuccess(1))
      if (isForked) await(awaitCallbacks)
      assertEquals(effect, 1)
    }
  }

  def executeOnErrorTest(
    wrap: Callback[Throwable, String] => Callback[Throwable, String],
    isForked: Boolean = false,
    retries: Int = RETRIES)(implicit sc: Scheduler): Unit = {

    for (_ <- 0 until retries) {
      var effect = 0
      val awaitCallbacks = if (isForked) new CountDownLatch(1) else null

      val cb = wrap(new Callback[Throwable, String] {
        override def onSuccess(value: String): Unit = ()
        override def onError(e: Throwable): Unit = {
          effect += 1
          if (isForked) awaitCallbacks.countDown()
        }
      })

      val e = DummyException("dummy")
      runConcurrently(sc)(cb.tryOnError(e))
      if (isForked) await(awaitCallbacks)
      assertEquals(effect, 1)
    }
  }

  def runConcurrently(sc: Scheduler)(f: => Unit): Unit = {
    val latchWorkersStart = new CountDownLatch(WORKERS)
    val latchWorkersFinished = new CountDownLatch(WORKERS)

    for (_ <- 0 until WORKERS) {
      sc.executeAsync { () =>
        latchWorkersStart.countDown()
        try {
          f
        } finally {
          latchWorkersFinished.countDown()
        }
      }
    }

    await(latchWorkersStart)
    await(latchWorkersFinished)
  }

  def await(latch: CountDownLatch): Unit = {
    val seconds = 10
    assert(latch.await(seconds, TimeUnit.SECONDS), s"latch.await($seconds seconds)")
  }
}
