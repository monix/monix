/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.eval.internal

import monix.eval.{Callback, Task}
import monix.execution.Scheduler
import monix.execution.cancelables.{CompositeCancelable, StackedCancelable}
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

private[monix] object TaskGatherUnordered {
  /**
    * Implementation for `Task.gatherUnordered`
    */
  def apply[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] = {

    Task.unsafeCreate { (scheduler, conn, frameRef, finalCallback) =>
      // We need a monitor to synchronize on, per evaluation!
      val lock = new AnyRef

      // Forced asynchronous boundary
      scheduler.executeAsyncBatch(lock.synchronized {
        // Aggregates all results into a buffer.
        // MUST BE synchronized by `lock`!
        var builder = cbf(in)

        // Keeps track of tasks remaining to be completed.
        // Is initialized by 1 because of the logic - tasks can run synchronously,
        // and we decrement this value whenever one finishes, so we must prevent
        // zero values before the loop is done.
        // MUST BE synchronized by `lock`!
        var remaining = 1

        // If this variable is false, then a task ended in error.
        // MUST BE synchronized by `lock`!
        var isActive = true

        // MUST BE synchronized by `lock`!
        // MUST NOT BE called if isActive == false!
        @inline def maybeSignalFinal(conn: StackedCancelable, finalCallback: Callback[M[A]])
          (implicit s: Scheduler): Unit = {

          remaining -= 1
          if (remaining == 0) {
            isActive = false
            conn.pop()
            val result = builder.result()
            builder = null // GC relief
            finalCallback.asyncOnSuccess(result)
          }
        }

        // MUST BE synchronized by `lock`!
        @inline def reportError(ex: Throwable)(implicit s: Scheduler): Unit = {
          if (isActive) {
            isActive = false
            // This should cancel our CompositeCancelable
            conn.pop().cancel()
            finalCallback.asyncOnError(ex)
            builder = null // GC relief
          } else {
            scheduler.reportFailure(ex)
          }
        }

        try {
          implicit val s = scheduler

          // Resetting the frame just for safety, since we clearly have
          // a real async boundary here
          frameRef.reset()

          // Represents the collection of cancelables for all started tasks
          val composite = CompositeCancelable()
          conn.push(composite)

          // Collecting all cancelables in a buffer, because adding
          // cancelables one by one in our `CompositeCancelable` is
          // expensive, so we do it at the end
          val allCancelables = ListBuffer.empty[StackedCancelable]
          val cursor = in.toIterator

          // The `isActive` check short-circuits the process in case
          // we have a synchronous task that just completed in error
          while (cursor.hasNext && isActive) {
            remaining += 1
            val task = cursor.next()
            val stacked = StackedCancelable()
            allCancelables += stacked

            // Light asynchronous boundary
            Task.unsafeStartTrampolined(task, scheduler, stacked, frameRef,
              new Callback[A] {
                def onSuccess(value: A): Unit =
                  lock.synchronized {
                    if (isActive) {
                      builder += value
                      maybeSignalFinal(conn, finalCallback)
                    }
                  }

                def onError(ex: Throwable): Unit =
                  lock.synchronized(reportError(ex))
              })
          }

          // All tasks could have executed synchronously, so we might be
          // finished already. If so, then trigger the final callback.
          maybeSignalFinal(conn, finalCallback)

          // Note that if an error happened, this should cancel all
          // other active tasks.
          composite ++= allCancelables
        }
        catch {
          case NonFatal(ex) =>
            reportError(ex)(scheduler)
        }
      })
    }
  }
}
