/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
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

package monifu.concurrent

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.{BlockContext, CanAwait, ExecutionContext}
import scala.util.control.NonFatal


/**
 * An execution context that runs scheduled tasks synchronously.
 *
 * @param fallback is the fallback `ExecutionContext` used for rescheduling
 *                 pending tasks in case the currently running task either
 *                 triggers an error or is executing a blocking task.
 */
final class TrampolinedExecutionContext private[concurrent] (fallback: ExecutionContext)
  extends ExecutionContext with BlockContext {

  private[this] val immediateQueue = ThreadLocal(Queue.empty[Runnable])
  private[this] val withinLoop = ThreadLocal(false)
  private[this] val parentBlockContext = ThreadLocal(null : BlockContext)
  private[this] val duringBlocking = ThreadLocal(false)

  def execute(runnable: Runnable): Unit =
    if (!duringBlocking.get()) {
      immediateQueue set immediateQueue.get().enqueue(runnable)
      if (!withinLoop.get) {
        withinLoop set true
        try immediateLoop() finally withinLoop set false
      }
    }
    else {
      fallback.execute(runnable)
    }

  @tailrec
  private[this] def immediateLoop(): Unit = {
    if (immediateQueue.get.nonEmpty) {
      val task = {
        val (t, newQueue) = immediateQueue.get.dequeue
        immediateQueue set newQueue
        t
      }

      val prevBlockContext = BlockContext.current
      BlockContext.withBlockContext(this) {
        parentBlockContext set prevBlockContext

        try {
          task.run()
        }
        catch {
          case NonFatal(ex) =>
            // exception in the immediate scheduler must be reported
            // but first we reschedule the pending tasks on the fallback
            try { rescheduleOnFallback(immediateQueue.get) } finally {
              immediateQueue set Queue.empty
              reportFailure(ex)
            }
        }
        finally {
          parentBlockContext set null
        }
      }

      immediateLoop()
    }
  }

  // if we know that a task will be blocking, then reschedule all
  // pending tasks on the fallback Scheduler
  def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
    duringBlocking set true
    try {
      val queue = immediateQueue.get()
      immediateQueue set Queue.empty
      rescheduleOnFallback(queue)
      parentBlockContext.get.blockOn(thunk)
    }
    finally {
      duringBlocking set false
    }
  }

  @tailrec
  private[this] def rescheduleOnFallback(queue: Queue[Runnable]): Unit =
    if (queue.nonEmpty) {
      val (task, newQueue) = queue.dequeue
      fallback.execute(task)
      rescheduleOnFallback(newQueue)
    }

  def reportFailure(t: Throwable): Unit =
    fallback.reportFailure(t)
}

object TrampolinedExecutionContext {
  def apply(fallback: ExecutionContext): TrampolinedExecutionContext =
    new TrampolinedExecutionContext(fallback)

  object Implicits {
    implicit lazy val global: ExecutionContext =
      apply(ExecutionContext.Implicits.global)
  }
}
