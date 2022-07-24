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

package monix.catnap

import cats.effect.IO
import monix.execution.BufferCapacity.Bounded
import monix.execution.{ BufferCapacity, Scheduler }
import monix.execution.schedulers.SchedulerService
import scala.concurrent.duration._

abstract class ConcurrentChannelJVMSuite(parallelism: Int) extends BaseConcurrentChannelSuite[SchedulerService] {
  val taskTimeout = 60.seconds

  def setup(): SchedulerService =
    Scheduler.computation(
      name = s"concurrent-channel-par-$parallelism",
      parallelism = parallelism
    )

  def tearDown(env: SchedulerService): Unit = {
    env.shutdown()
    assert(env.awaitTermination(30.seconds), "env.awaitTermination")
  }

  def testIO(name: String, times: Int = 1)(f: Scheduler => IO[Unit]): Unit = {
    def repeatTest(test: IO[Unit], n: Int): IO[Unit] =
      if (n > 0) test.flatMap(_ => repeatTest(test, n - 1))
      else IO.unit

    fixture.test(name) { implicit ec =>
      repeatTest(f(ec).timeout(taskTimeout), times).unsafeToFuture()
    }
  }

  val boundedConfigForConcurrentSum: BufferCapacity.Bounded =
    Bounded(32)
}

class ConcurrentChannelJVMParallelism8Suite extends ConcurrentChannelJVMSuite(8)
class ConcurrentChannelJVMParallelism4Suite extends ConcurrentChannelJVMSuite(4)
class ConcurrentChannelJVMParallelism1Suite extends ConcurrentChannelJVMSuite(1)
