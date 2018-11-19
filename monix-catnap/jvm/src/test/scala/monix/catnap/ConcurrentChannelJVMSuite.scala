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

package monix.catnap

import cats.effect.IO
import monix.execution.BufferCapacity.Bounded
import monix.execution.{BufferCapacity, Scheduler}
import monix.execution.schedulers.SchedulerService

import scala.concurrent.duration._

object ConcurrentChannelJVMSuite extends BaseConcurrentChannelSuite[SchedulerService] {
  def setup(): SchedulerService =
    Scheduler.computation(
      name = "concurrent-channel-tests",
      parallelism = 4
    )

  def tearDown(env:  SchedulerService): Unit = {
    env.shutdown()
    env.awaitTermination(30.seconds)
  }

  def testIO(name:  String)(f:  Scheduler => IO[Unit]): Unit =
    testAsync(name) { implicit ec =>
      f(ec).timeout(30.second).unsafeToFuture()
    }

  val boundedConfigForConcurrentSum: BufferCapacity.Bounded =
    Bounded(256)
}
