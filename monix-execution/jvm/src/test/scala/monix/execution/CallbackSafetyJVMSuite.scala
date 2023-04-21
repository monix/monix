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

package monix.execution

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import minitest.TestSuite
import minitest.api.{ AssertionException, MiniTestException }
import monix.execution.exceptions.{ CallbackCalledMultipleTimesException, DummyException }
import monix.execution.schedulers.SchedulerService

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

object CallbackSafetyJVMSuite extends TestSuite[SchedulerService] with TestUtils {
  val WORKERS = 10
  val RETRIES = if (!isCI) 1000 else 100
  val DUMMY = DummyException("dummy")

  override def setup(): SchedulerService =
    Scheduler.io("test-callback")

  override def tearDown(env: SchedulerService): Unit = {
    env.shutdown()
    env.awaitTermination(10.seconds)
    ()
  }

  test("Callback.safe is thread-safe onSuccess") { implicit sc =>
    executeOnSuccessTest(Callback.safe)
  }

  test("Callback.safe is thread-safe onError") { implicit sc =>
    executeOnErrorTest(Callback.safe)
  }

  test("Callback.trampolined is thread-safe onSuccess") { implicit sc =>
    executeOnSuccessTest(Callback.trampolined)
  }

  test("Callback.trampolined is thread-safe onError") { implicit sc =>
    executeOnErrorTest(Callback.trampolined)
  }

  test("Callback.forked is thread-safe onSuccess") { implicit sc =>
    executeOnSuccessTest(Callback.forked, isForked = true)
  }

  test("Callback.forked is thread-safe onError") { implicit sc =>
    executeOnErrorTest(Callback.forked, isForked = true)
  }

  test("Callback.fromPromise is thread-safe onSuccess") { implicit sc =>
    val wrap = { (cb: Callback[Throwable, Int]) =>
      val p = Promise[Int]()
      p.future.onComplete(cb.apply)
      Callback.fromPromise(p)
    }
    executeOnSuccessTest(wrap, isForked = true)
  }

  test("Callback.fromPromise is thread-safe onError") { implicit sc =>
    val wrap = { (cb: Callback[Throwable, String]) =>
      val p = Promise[String]()
      p.future.onComplete(cb.apply)
      Callback.fromPromise(p)
    }
    executeOnErrorTest(wrap, isForked = true)
  }

  test("Normal callback is not thread-safe via onSuccess") { implicit sc =>
    intercept[AssertionException] { executeOnSuccessTest(x => x) }
    ()
  }

  test("Normal callback is not thread-safe via onError") { implicit sc =>
    intercept[AssertionException] { executeOnErrorTest(x => x) }
    ()
  }

  test("Callback.fromAttempt is not thread-safe via onSuccess") { implicit sc =>
    if (isCI) ignore()

    val wrap = { (cb: Callback[Throwable, Int]) =>
      val f = (r: Either[Throwable, Int]) => cb(r)
      Callback.fromAttempt(f)
    }
    intercept[AssertionException] { executeOnSuccessTest(wrap, retries = RETRIES * 100) }
    ()
  }

  test("Callback.fromAttempt is not thread-safe via onError") { implicit sc =>
    if (isCI) ignore()

    val wrap = { (cb: Callback[Throwable, String]) =>
      val f = (r: Either[Throwable, String]) => cb(r)
      Callback.fromAttempt(f)
    }
    intercept[AssertionException] { executeOnErrorTest(wrap, retries = RETRIES * 100) }
    ()
  }

  test("Callback.fromTry is not thread-safe via onSuccess") { implicit sc =>
    if (isCI) ignore()

    val wrap = { (cb: Callback[Throwable, Int]) =>
      val f = (r: Try[Int]) => cb(r)
      Callback.fromTry(f)
    }
    intercept[AssertionException] { executeOnSuccessTest(wrap, retries = RETRIES * 100) }
    ()
  }

  test("Callback.fromTry is not thread-safe via onError") { implicit sc =>
    if (isCI) ignore()

    val wrap = { (cb: Callback[Throwable, String]) =>
      val f = (r: Try[String]) => cb(r)
      Callback.fromTry(f)
    }
    intercept[AssertionException] { executeOnErrorTest(wrap, retries = RETRIES * 100) }
    ()
  }

  test("Callback.fromAttempt is quasi-safe via onSuccess") { _ =>
    executeQuasiSafeOnSuccessTest { cb =>
      val f = (r: Either[Throwable, Int]) => cb(r)
      Callback.fromAttempt(f)
    }
  }

  test("Callback.fromAttempt is quasi-safe via onError") { _ =>
    executeQuasiSafeOnFailureTest { cb =>
      val f = (r: Either[Throwable, Int]) => cb(r)
      Callback.fromAttempt(f)
    }
  }

  test("Callback.fromTry is quasi-safe via onSuccess") { _ =>
    executeQuasiSafeOnSuccessTest { cb =>
      val f = (r: Try[Int]) => cb(r)
      Callback.fromTry(f)
    }
  }

  test("Callback.fromTry is quasi-safe via onError") { _ =>
    executeQuasiSafeOnFailureTest { cb =>
      val f = (r: Try[Int]) => cb(r)
      Callback.fromTry(f)
    }
  }

  test("Normal callback is not quasi-safe via onSuccess") { _ =>
    intercept[MiniTestException] {
      executeQuasiSafeOnSuccessTest(x => x)
    }
    ()
  }

  test("Normal callback is not quasi-safe via onError") { _ =>
    intercept[MiniTestException] {
      executeQuasiSafeOnFailureTest(x => x)
    }
    ()
  }

  def executeQuasiSafeOnSuccessTest(wrap: Callback[Throwable, Int] => Callback[Throwable, Int]): Unit = {
    def run(trigger: Callback[Throwable, Int] => Unit, tryTrigger: Callback[Throwable, Int] => Boolean): Unit = {

      var effect = 0
      val cb = wrap(new Callback[Throwable, Int] {
        override def onSuccess(value: Int): Unit =
          effect += value
        override def onError(e: Throwable): Unit =
          ()
      })

      assert(tryTrigger(cb), "cb.tryOnSuccess(1)")
      assert(!tryTrigger(cb), "!cb.tryOnSuccess(1)")

      intercept[CallbackCalledMultipleTimesException] { trigger(cb) }
      assertEquals(effect, 1)
    }

    run(_.onSuccess(1), _.tryOnSuccess(1))
    run(_.apply(Right(1)), _.tryApply(Right(1)))
    run(_.apply(Success(1)), _.tryApply(Success(1)))
  }

  def executeQuasiSafeOnFailureTest(wrap: Callback[Throwable, Int] => Callback[Throwable, Int]): Unit = {
    def run(trigger: Callback[Throwable, Int] => Unit, tryTrigger: Callback[Throwable, Int] => Boolean): Unit = {

      var effect = 0
      val cb = wrap(new Callback[Throwable, Int] {
        override def onSuccess(value: Int): Unit =
          ()
        override def onError(e: Throwable): Unit =
          effect += 1
      })

      assert(tryTrigger(cb), "cb.tryOnError(1)")
      assert(!tryTrigger(cb), "!cb.tryOnError(1)")
      intercept[CallbackCalledMultipleTimesException] { trigger(cb) }
      assertEquals(effect, 1)
    }

    run(_.onError(DUMMY), _.tryOnError(DUMMY))
    run(_.apply(Left(DUMMY)), _.tryApply(Left(DUMMY)))
    run(_.apply(Failure(DUMMY)), _.tryApply(Failure(DUMMY)))
  }

  def executeOnSuccessTest(
    wrap: Callback[Throwable, Int] => Callback[Throwable, Int],
    isForked: Boolean = false,
    retries: Int = RETRIES
  )(implicit sc: Scheduler): Unit = {

    def run(trigger: Callback[Throwable, Int] => Any): Unit = {
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

        runConcurrently(sc)(trigger(cb))
        if (isForked) await(awaitCallbacks)
        assertEquals(effect, 1)
      }
    }

    run(_.tryOnSuccess(1))
    run(_.tryApply(Right(1)))
    run(_.tryApply(Success(1)))

    run(cb =>
      try cb.onSuccess(1)
      catch { case _: CallbackCalledMultipleTimesException => () }
    )
    run(cb =>
      try cb(Right(1))
      catch { case _: CallbackCalledMultipleTimesException => () }
    )
    run(cb =>
      try cb(Success(1))
      catch { case _: CallbackCalledMultipleTimesException => () }
    )
  }

  def executeOnErrorTest(
    wrap: Callback[Throwable, String] => Callback[Throwable, String],
    isForked: Boolean = false,
    retries: Int = RETRIES
  )(implicit sc: Scheduler): Unit = {

    def run(trigger: Callback[Throwable, String] => Any): Unit = {
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

        runConcurrently(sc)(trigger(cb))
        if (isForked) await(awaitCallbacks)
        assertEquals(effect, 1)
      }
    }

    run(_.tryOnError(DUMMY))
    run(_.tryApply(Left(DUMMY)))
    run(_.tryApply(Failure(DUMMY)))

    run(cb =>
      try cb.onError(DUMMY)
      catch { case _: CallbackCalledMultipleTimesException => () }
    )
    run(cb =>
      try cb.tryApply(Left(DUMMY))
      catch { case _: CallbackCalledMultipleTimesException => () }
    )
    run(cb =>
      try cb.tryApply(Failure(DUMMY))
      catch { case _: CallbackCalledMultipleTimesException => () }
    )
  }

  def runConcurrently(sc: Scheduler)(f: => Any): Unit = {
    val latchWorkersStart = new CountDownLatch(WORKERS)
    val latchWorkersFinished = new CountDownLatch(WORKERS)

    for (_ <- 0 until WORKERS) {
      sc.execute { () =>
        latchWorkersStart.countDown()
        try { f: Unit }
        finally latchWorkersFinished.countDown()
      }
    }

    await(latchWorkersStart)
    await(latchWorkersFinished)
  }

  def await(latch: CountDownLatch): Unit = {
    val seconds = 10
    assert(latch.await(seconds.toLong, TimeUnit.SECONDS), s"latch.await($seconds seconds)")
  }
}
