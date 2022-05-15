/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

import minitest.TestSuite
import monix.execution.FutureUtils.extensions._
import monix.execution.schedulers.TestScheduler

import scala.concurrent.Future
import scala.concurrent.duration._

object FutureUtilsJVMSuite extends TestSuite[TestScheduler] {

  def setup() = TestScheduler()

  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")
  }

  testAsync("timeoutTo should not evaluate fallback when future and timeout are finished at the same time") { _ =>
    implicit val scheduler: Scheduler = Scheduler(Executors.newWorkStealingPool())

    case class TestException() extends RuntimeException

    val total = new AtomicLong(0)
    val sideEffect = new AtomicLong(0)
    val error = new AtomicLong(0)
    val success = new AtomicLong(0)

    val originalTimeout = 50.millis

    def runFuture(timeout: FiniteDuration): Future[Unit] = {
      total.incrementAndGet()

      (for {
        _ <- Future
          .delayedResult(originalTimeout) {
            15
          }
          .timeoutTo(
            timeout, {
              sideEffect.incrementAndGet()
              Future.failed(TestException())
            })(Scheduler.Implicits.global)
        _ <- FutureUtils.delayedResult(100.millis)(()) // wait for all concurrent processes
      } yield ()).map { _ =>
        success.incrementAndGet()
        ()
      }.recover {
        case _: TestException =>
          error.incrementAndGet()
          ()
      }
    }

    // this function runs a lot of futures and tries to adjust timeouts to catch race condition,
    // when some futures finish with success and some finish with a timeout
    def waitingForRace(timeout: FiniteDuration): Future[FiniteDuration] = {
      total.set(0)
      sideEffect.set(0)
      error.set(0)
      success.set(0)

      val futures = (1 to 10000).map { _ =>
        runFuture(timeout)
      }

      Future.sequence(futures).flatMap { _ =>
        if (total.get == error.get) {
          waitingForRace(timeout + 1.millis)
        } else if (total.get == success.get) {
          waitingForRace(timeout - 1.millis)
        } else {
          Future.successful(timeout)
        }
      }
    }

    waitingForRace(originalTimeout).map { _ =>
      assertEquals(error.get + success.get, total.get)
      assertEquals(sideEffect.get, error.get)
    }
  }

}
