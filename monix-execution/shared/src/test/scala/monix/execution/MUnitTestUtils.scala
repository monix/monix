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

import monix.execution.schedulers.SchedulerService
import org.scalacheck.Arbitrary
import org.scalacheck.Prop
import org.scalacheck.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.TimeoutException
import scala.concurrent.blocking
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure

/** Base trait for all MUnit-based Monix test suites. */
trait MUnitFunSuite extends munit.FunSuite {
  override val munitTimeout: FiniteDuration = 30.seconds
  val awaitTimeout: FiniteDuration = 20.seconds

  override def isCI: Boolean =
    monix.execution.internal.Platform.getEnv("CI").map(_.toLowerCase).contains("true")

  override def munitValueTransforms: List[ValueTransform] = {
    import Scheduler.Implicits.global
    val alreadyDefined = super.munitValueTransforms
    val forCancelableFuture =
      new ValueTransform(
        "CancelableFuture",
        {
          case ref: CancelableFuture[_] =>
            ref.timeoutTo(
              munitTimeout,
              Failure(
                new TimeoutException(s"Test timed-out after $munitTimeout")
              )
            )
        }
      )
    forCancelableFuture :: alreadyDefined
  }

  implicit def laxCompare[A, B]: munit.Compare[A, B] =
    munit.Compare.defaultCompare[A, B]

  def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default
      .withMinSuccessfulTests(if (monix.execution.internal.Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (monix.execution.internal.Platform.isJVM) 5.0f else 50.0f)

  def check2[A: Arbitrary, B: Arbitrary](f: (A, B) => Prop): Unit = {
    val result = Test.check(scalaCheckTestParameters, Prop.forAll(f))
    assert(result.passed, clue(result.toString))
  }

  def tryAwait(latch: CountDownLatch): Boolean =
    blocking {
      latch.await(awaitTimeout.length, awaitTimeout.unit)
    }

  def await(latch: CountDownLatch, name: String = "latch"): Unit =
    blocking {
      assert(tryAwait(latch), s"Timed-out waiting for `$latch` to complete after $awaitTimeout")
    }
}

trait MUnitFixtureSuite[A] extends MUnitFunSuite { self =>
  def setup(): A

  def tearDown(env: A): Unit

  def test(name: String)(property: A => Any)(implicit loc: munit.Location): Unit =
    withEnv.test(name) { env =>
      property(env)
    }

  private val withEnv = FunFixture[A](
    setup = { _ =>
      self.setup()
    },
    teardown = { env =>
      self.tearDown(env)
    }
  )
}

trait SchedulerServiceSuite extends MUnitFixtureSuite[SchedulerService] {
  def setup(): SchedulerService

  override def tearDown(env: SchedulerService): Unit =
    env.shutdown()
}
