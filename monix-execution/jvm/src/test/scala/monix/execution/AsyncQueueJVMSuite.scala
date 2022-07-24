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
import scala.concurrent.Future
import scala.concurrent.duration._

abstract class AsyncQueueJVMSuite(parallelism: Int) extends BaseAsyncQueueSuite[SchedulerService] {

  def setup(): SchedulerService =
    Scheduler.computation(
      name = s"concurrent-queue-par-$parallelism",
      parallelism = parallelism
    )

  def tearDown(env: SchedulerService): Unit = {
    env.shutdown()
    assert(env.awaitTermination(30.seconds), "env.awaitTermination")
  }

  def testFuture(name: String, times: Int)(f: Scheduler => Future[Unit]): Unit = {
    def repeatTest(test: Future[Unit], n: Int)(implicit ec: Scheduler): Future[Unit] =
      if (n > 0)
        FutureUtils
          .timeout(test, 60.seconds)
          .flatMap(_ => repeatTest(test, n - 1))
      else
        Future.successful(())

    fixture.test(name) { implicit ec =>
      repeatTest(f(ec), times)
    }
  }
}

class AsyncQueueJVMParallelism8Suite extends AsyncQueueJVMSuite(8)
class AsyncQueueJVMParallelism4Suite extends AsyncQueueJVMSuite(4)
class AsyncQueueJVMParallelism1Suite extends AsyncQueueJVMSuite(1)
