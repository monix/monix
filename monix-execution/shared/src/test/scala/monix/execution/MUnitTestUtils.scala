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
import munit.Compare
import munit.FunSuite
import munit.Location
import org.scalacheck.Arbitrary
import org.scalacheck.Prop
import org.scalacheck.Test

import java.util.concurrent.locks.ReentrantLock
import scala.concurrent.Future
import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/** Base trait for all MUnit-based Monix test suites. */
trait MUnitFunSuite extends FunSuite {
  override def isCI: Boolean =
    monix.execution.internal.Platform.getEnv("CI").map(_.toLowerCase).contains("true")

  override def munitTimeout: FiniteDuration =
    if (isCI) 30.seconds
    else 10.seconds

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

  implicit def laxCompare[A, B]: Compare[A, B] = Compare.defaultCompare[A, B]

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

  def test(name: String)(property: A => Any)(implicit loc: Location): Unit =
    super[MUnitFunSuite].test(name) {
      import scala.concurrent.ExecutionContext.Implicits.global
      val env = setup()
      val result = Try(property(env))
      result match {
        case Success(f: Future[_]) =>
          f.transform {
            case Success(value) =>
              Try(tearDown(env)).map(_ => value)
            case Failure(e) =>
              Try(tearDown(env)).failed.foreach(e.addSuppressed)
              Failure(e)
          }
        case Success(value) =>
          tearDown(env)
          value
        case Failure(e) =>
          Try(tearDown(env)).failed.foreach(e.addSuppressed)
          throw e
      }
    }
}

trait SchedulerServiceSuite extends MUnitFixtureSuite[SchedulerService] {
  def setup(): SchedulerService

  override def tearDown(env: SchedulerService): Unit =
    env.shutdown()
}
