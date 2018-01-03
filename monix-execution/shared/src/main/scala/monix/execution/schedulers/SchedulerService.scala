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

import monix.execution.{ExecutionModel => ExecModel, Scheduler}
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

/** A [[monix.execution.Scheduler Scheduler]] type that provides
  * methods for managing termination.
  *
  * A `SchedulerService` can be shut down, which will cause it to reject
  * new tasks. The `shutdown` method allows previously submitted tasks to
  * execute before terminating. The `awaitTermination` method allows
  * waiting on all active tasks to finish.
  *
  * Upon termination, an executor has no tasks actively executing, no tasks
  * awaiting execution, and no new tasks can be submitted. An unused
  * `SchedulerService` should be shut down to allow reclamation of
  * its resources.
  */
trait SchedulerService extends Scheduler {
  /** Returns `true` if this scheduler has been shut down. */
  def isShutdown: Boolean

  /** Returns `true` if all tasks have completed following shut down.
    * Note that `isTerminated` is never `true` unless [[shutdown]]
    * was called first.
    */
  def isTerminated: Boolean

  /** Initiates an orderly shutdown in which previously submitted
    * tasks are executed, but no new tasks will be accepted.
    *
    * This method does not wait for previously submitted tasks to
    * complete execution. Use [[awaitTermination]] to do that.
    */
  def shutdown(): Unit

  /** Returns a `Future` that will be complete when
    * all tasks have completed execution after a [[shutdown]] request,
    * or the `timeout` occurs, or the thread awaiting the shutdown
    * is interrupted, whichever happens first.
    *
    * NOTE that this method does not block the current thread,
    * unlike the similarly named method in Java's `ExecutionService`.
    * This is because Monix has a strict non-blocking policy, due to the
    * fact that other platforms like Javascript cannot block threads.
    *
    * Because of the non-blocking requirement, this method returns
    * a `Future` result. And on top of the JVM in order to block on
    * such a result, you can just use Scala's `Await.result`:
    *
    * {{{
    *   import scala.concurrent.Await
    *   import scala.concurrent.duration._
    *   import monix.execution.Scheduler.global
    *
    *   val wasTerminated =
    *     Await.result(service.awaitTermination(30.seconds, global), Duration.Inf)
    * }}}
    *
    * Given the asynchronous execution requirement, the `awaitOn` parameter
    * is a [[Scheduler]] that's going to be used for terminating this
    * service and completing our `Future`. Obviously we cannot reuse
    * this service for awaiting on termination, but Monix's
    * [[monix.execution.Scheduler.global Scheduler.global]]
    * can always be used for this.
    *
    * @param timeout the maximum time to wait
    * @param unit the time unit of the timeout argument
    * @param awaitOn the [[Scheduler]] used for awaiting the shutdown
    *
    * @return a `Future` signaling `true` if this scheduler terminated or
    *         `false` if the timeout elapsed before termination
    */
  def awaitTermination(timeout: Long, unit: TimeUnit, awaitOn: Scheduler): Future[Boolean]

  // Overriding the return type
  override def withExecutionModel(em: ExecModel): SchedulerService
}

object SchedulerService {
  /** Extensions for the [[SchedulerService]] interface. */
  implicit class Extensions(val self: SchedulerService) extends AnyVal {
    /** Overload of [[SchedulerService.awaitTermination]]. */
    def awaitTermination(timeout: FiniteDuration, awaitOn: Scheduler): Future[Boolean] =
      self.awaitTermination(timeout.length, timeout.unit, awaitOn)
  }
}