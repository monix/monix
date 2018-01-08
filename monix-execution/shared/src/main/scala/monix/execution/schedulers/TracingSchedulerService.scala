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

package monix.execution.schedulers

import monix.execution.{ExecutionModel, Scheduler}

import scala.concurrent.Future
import scala.concurrent.duration.TimeUnit

/** The `TracingScheduler` is a [[monix.execution.Scheduler Scheduler]]
  * implementation that wraps another
  * [[monix.execution.schedulers.SchedulerService SchedulerService]]
  * reference, with the purpose of propagating the
  * [[monix.execution.misc.Local.Context Local.Context]] on async
  * execution.
  *
  * @param underlying the
  *        [[monix.execution.schedulers.SchedulerService SchedulerService]]
  *        in charge of the actual execution and scheduling
  */
final class TracingSchedulerService(underlying: SchedulerService)
  extends TracingScheduler.Base(underlying)
  with SchedulerService { self =>

  override def isShutdown: Boolean =
    underlying.isShutdown
  override def isTerminated: Boolean =
    underlying.isTerminated
  override def shutdown(): Unit =
    underlying.shutdown()
  override def awaitTermination(timeout: Long, unit: TimeUnit, awaitOn: Scheduler): Future[Boolean] =
    underlying.awaitTermination(timeout, unit, awaitOn)
  override def withExecutionModel(em: ExecutionModel): TracingSchedulerService =
    new TracingSchedulerService(underlying.withExecutionModel(em))
}

object TracingSchedulerService {
  /** Builds a [[TracingScheduler]] instance, wrapping the
    * `underlying` scheduler given.
    */
  def apply(underlying: SchedulerService): TracingSchedulerService =
    new TracingSchedulerService(underlying)
}
