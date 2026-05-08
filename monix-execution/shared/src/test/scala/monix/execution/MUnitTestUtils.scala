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

import java.util.concurrent.TimeUnit

import monix.execution.schedulers.SchedulerService
import monix.execution.schedulers.TestScheduler
import munit.FunSuite
import munit.Location
import munit.Compare
import org.scalacheck.{ Arbitrary, Prop, Test }

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

/** Base trait for all MUnit-based Monix test suites. */
trait MUnitFunSuite extends FunSuite {
  override def isCI: Boolean =
    monix.execution.internal.Platform.getEnv("CI").map(_.toLowerCase).contains("true")

  implicit def laxCompare[A, B]: Compare[A, B] = Compare.defaultCompare[A, B]

  def testAsync(name: String)(body: => Future[Any])(implicit loc: Location): Unit =
    test(name)(body)

  def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default
      .withMinSuccessfulTests(if (monix.execution.internal.Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (monix.execution.internal.Platform.isJVM) 5.0f else 50.0f)

  def check2[A: Arbitrary, B: Arbitrary](f: (A, B) => Prop): Unit = {
    val result = Test.check(scalaCheckTestParameters, Prop.forAll(f))
    assert(result.passed, clue(result.toString))
  }
}

trait MUnitFixtureSuite[A] extends MUnitFunSuite {
  def setup(): A

  def tearDown(env: A): Unit

  def test(name: String)(body: A => Any)(implicit loc: Location): Unit =
    super[MUnitFunSuite].test(name) {
      val env = setup()
      try body(env)
      finally tearDown(env)
    }

  def testAsync(name: String)(body: A => Future[Any])(implicit loc: Location): Unit =
    super[MUnitFunSuite].test(name) {
      val env = setup()
      try body(env).transformWith {
        case Success(result) =>
          try Future.successful { tearDown(env); result }
          catch { case NonFatal(e) => Future.failed(e) }
        case Failure(e) =>
          try tearDown(env)
          catch { case NonFatal(teardownError) => e.addSuppressed(teardownError) }
          Future.failed(e)
      }(munitExecutionContext)
      catch {
        case NonFatal(e) =>
          tearDown(env)
          Future.failed(e)
      }
    }
}

/** Replaces minitest.TestSuite[TestScheduler] with MUnit-friendly helpers.
  * Creates a fresh TestScheduler per test and asserts no pending tasks remain after each test.
  */
trait TestSchedulerSuite extends MUnitFixtureSuite[TestScheduler] {

  /** Creates a fresh TestScheduler for each test. */
  def createTestScheduler(): TestScheduler = TestScheduler()

  override def setup(): TestScheduler = createTestScheduler()

  override def tearDown(s: TestScheduler): Unit = assertNoRemainingTasks(s)

  /** Verify no remaining tasks on scheduler. Calls assertEquals for MUnit diff diagnostics. */
  def assertNoRemainingTasks(s: TestScheduler): Unit = {
    assertEquals(
      clue(s.state.tasks.isEmpty),
      true,
      clue("should not have tasks left to execute")
    )
  }

  /** Synchronous test with TestScheduler injected. Teardown runs after body completes. */
  def testScheduler(name: String)(body: TestScheduler => Any)(implicit loc: Location): Unit = {
    test(name)(body)
  }

  /** Async test with TestScheduler. Teardown runs AFTER the returned Future completes.
    * This is critical: do NOT teardown synchronously around a returned Future.
    */
  def testSchedulerAsync(name: String)(body: TestScheduler => Future[Any])(implicit loc: Location): Unit = {
    testAsync(name)(body)
  }

  /** Run body with a fresh TestScheduler and teardown assertion.
    * Use for one-off scheduler-based assertions inside tests.
    */
  def withTestScheduler[A](body: TestScheduler => A): A = {
    val s = createTestScheduler()
    val result = body(s)
    assertNoRemainingTasks(s)
    result
  }
}

/** For JVM-only suites that need a real SchedulerService (e.g. Scheduler.computation, Scheduler.io).
  * NOT available on Scala.js — use `TestSchedulerSuite` for cross-platform tests.
  */
trait SchedulerServiceSuite extends MUnitFunSuite {

  /** Override in concrete suites to provide the service. */
  def createSchedulerService(): SchedulerService

  /** Synchronous test with SchedulerService. Service is shut down after the test body completes. */
  def testService(name: String)(body: SchedulerService => Any)(implicit loc: Location): Unit = {
    test(name) {
      val service = createSchedulerService()
      try {
        body(service)
        shutdownService(service)
      } catch {
        case NonFatal(e) => shutdownService(service).flatMap(_ => Future.failed(e))(munitExecutionContext)
      }
    }
  }

  /** Async test with SchedulerService. Shutdown occurs after the returned Future completes. */
  def testServiceAsync(name: String)(body: SchedulerService => Future[Any])(implicit loc: Location): Unit = {
    test(name) {
      val service = createSchedulerService()
      try body(service).transformWith {
        case Success(_) =>
          shutdownService(service)
        case Failure(e) =>
          shutdownService(service).transformWith {
            case Success(_) => Future.failed(e)
            case Failure(shutdownError) =>
              e.addSuppressed(shutdownError)
              Future.failed(e)
          }(munitExecutionContext)
      }(munitExecutionContext)
      catch {
        case NonFatal(e) => shutdownService(service).flatMap(_ => Future.failed(e))(munitExecutionContext)
      }
    }
  }

  private def shutdownService(service: SchedulerService): Future[Unit] = {
    service.shutdown()
    service.awaitTermination(30, TimeUnit.SECONDS, Scheduler.global).map { terminated =>
      assertEquals(clue(terminated), true, clue("service should terminate"))
    }(munitExecutionContext)
  }
}
