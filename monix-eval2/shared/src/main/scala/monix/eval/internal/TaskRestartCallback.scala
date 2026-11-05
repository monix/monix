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

package monix.eval
package internal

import monix.eval.Task.AsyncSimple
import monix.execution.exceptions.APIContractViolationException
import monix.execution.{ Callback, Scheduler }
import scala.util.control.NonFatal

/** A callback belongs to exactly one async suspension.
  *
  * The fiber checks this object's identity before accepting a result. Cancellation
  * can therefore abandon an operation while its callback is still in flight,
  * without letting that callback resume a later cancellation finalizer.
  */
private[internal] final class TaskRestartCallback(
  fiber: TaskFiber[_],
  scheduler: Scheduler
) extends Callback[Throwable, Any] {
  def start(task: AsyncSimple[_]): Unit =
    try task.register(scheduler, this)
    catch {
      case NonFatal(error) =>
        scheduler.reportFailure(
          new APIContractViolationException(
            "Task.AsyncSimple#start threw an unexpected exception, this is most likely a library bug",
            error
          )
        )
    }

  override def onSuccess(value: Any): Unit =
    signal(Task.Pure(value))

  override def onError(error: Throwable): Unit =
    signal(Task.RaiseError(error))

  private def signal(result: Task[Any]): Unit =
    try fiber.continueWithRef(result, this)
    catch {
      // A producer may be notifying several consumers (for example, Deferred).
      // Failure to schedule this fiber must not interrupt the producer's fan-out.
      case NonFatal(error) => scheduler.reportFailure(error)
    }
}
