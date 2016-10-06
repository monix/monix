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

private[monix] object TaskGather {
  /**
    * Implementation for `Task.gather`
    */
  def apply[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] = {

    Task.unsafeCreate { (scheduler, conn, frameRef, finalCallback) =>
      // We need a monitor to synchronize on, per evaluation!
      val lock = new AnyRef

      var tasks: Array[Task[A]] = null
      var results: Array[AnyRef] = null
      var tasksCount = 0
      var completed = 0

      // If this variable is false, then a task ended in error.
      // MUST BE synchronized by `lock`!
      var isActive = true

      // MUST BE synchronized by `lock`!
      // MUST NOT BE called if isActive == false!
      @inline def maybeSignalFinal(conn: StackedCancelable, finalCallback: Callback[M[A]])
        (implicit s: Scheduler): Unit = {

        completed += 1
        if (completed >= tasksCount) {
          isActive = false
          conn.pop()

          val builder = cbf(in)
          var idx = 0
          while (idx < results.length) {
            builder += results(idx).asInstanceOf[A]
            idx += 1
          }

          tasks = null // GC relief
          results = null // GC relief
          finalCallback.asyncOnSuccess(builder.result())
        }
      }

      // MUST BE synchronized by `lock`!
      @inline def reportError(ex: Throwable)(implicit s: Scheduler): Unit = {
        if (isActive) {
          isActive = false
          // This should cancel our CompositeCancelable
          conn.pop().cancel()
          tasks = null // GC relief
          results = null // GC relief
          finalCallback.asyncOnError(ex)
        } else {
          scheduler.reportFailure(ex)
        }
      }

      // Light asynchronous boundary
      scheduler.executeTrampolined(lock.synchronized {
        try {
          implicit val s = scheduler
          tasks = in.toArray
          tasksCount = tasks.length

          if (tasksCount == 0) {
            finalCallback.asyncOnSuccess(cbf(in).result())
          }
          else {
            results = new Array[AnyRef](tasksCount)

            // Collecting all cancelables in a buffer, because adding
            // cancelables one by one in our `CompositeCancelable` is
            // expensive, so we do it at the end
            val allCancelables = ListBuffer.empty[StackedCancelable]

            // We need a composite because we are potentially starting tasks
            // in paralel and thus we need to cancel everything
            val composite = CompositeCancelable()
            conn.push(composite)

            var idx = 0
            while (idx < tasksCount && isActive) {
              val currentTask = idx
              val stacked = StackedCancelable()
              allCancelables += stacked

              // Light asynchronous boundary
              Task.unsafeStartTrampolined(tasks(idx), scheduler, stacked, frameRef,
                new Callback[A] {
                  def onSuccess(value: A): Unit =
                    lock.synchronized {
                      if (isActive) {
                        results(currentTask) = value.asInstanceOf[AnyRef]
                        maybeSignalFinal(conn, finalCallback)
                      }
                    }

                  def onError(ex: Throwable): Unit =
                    lock.synchronized(reportError(ex))
                })

              idx += 1
            }

            // Note that if an error happened, this should cancel all
            // active tasks.
            composite ++= allCancelables
          }
        }
        catch {
          case NonFatal(ex) =>
            // We are still under the lock.synchronize block
            // so this call is safe
            reportError(ex)(scheduler)
        }
      })
    }
  }
}
