/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: https://monifu.org
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

import monifu.internals.collection.DropHeadOnOverflowQueue
import scala.annotation.tailrec
import scala.util.control.NonFatal

/**
 * A `Trampoline` is an `ExecutionContext` that is able to queue
 * and execute tasks on the current thread, by using a trampoline
 * as a construct.
 */
trait Trampoline {
  /**
   * Schedules a new task for execution on the trampoline.
   *
   * @return true if the task was scheduled, or false if it was
   *         rejected because the queue is full.
   */
  def execute(r: Runnable): Boolean
}

object Trampoline extends internals.TrampolineCompanion {
  /**
   * A simple and non-thread safe implementation of the [[Trampoline]].
   */
  final class Local(reporter: UncaughtExceptionReporter) extends Trampoline {
    private[this] val queue = DropHeadOnOverflowQueue[Runnable](10000)
    private[this] var loopStarted = false

    def execute(r: Runnable): Boolean = {
      if (loopStarted) {
        if (queue.isAtCapacity)
          false
        else {
          queue.offer(r)
          true
        }
      }
      else {
        loopStarted = true
        startLoop(r)
        true
      }
    }

    @tailrec
    private[this] def startLoop(r: Runnable): Unit = {
      val toRun = if (r != null) r
        else queue.poll()

      if (toRun != null) {
        try toRun.run()
        catch { case NonFatal(ex) => reporter.reportFailure(ex) }
        startLoop(null)
      } else {
        loopStarted = false
      }
    }
  }
}
