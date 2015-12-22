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

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/**
 * A `Trampoline` is an `ExecutionContext` that is able to queue
 * and execute tasks on the current thread, by using a trampoline
 * as a construct.
 */
trait Trampoline extends ExecutionContext {
  def execute(r: Runnable): Unit
}

object Trampoline extends internals.TrampolineCompanion {
  /**
   * A simple and non-thread safe implementation of the [[Trampoline]].
   */
  final class Local(reporter: UncaughtExceptionReporter) extends Trampoline {
    private[this] val queue = mutable.Queue.empty[Runnable]
    private[this] var loopStarted = false

    def execute(r: Runnable): Unit = {
      if (loopStarted)
        queue.enqueue(r)
      else {
        loopStarted = true
        startLoop(r)
      }
    }

    @tailrec
    private[this] def startLoop(r: Runnable): Unit = {
      val toRun = if (r != null) r
        else if (queue.nonEmpty) queue.dequeue()
        else null

      if (toRun != null) {
        try toRun.run() catch { case NonFatal(ex) => reportFailure(ex) }
        startLoop(null)
      } else {
        loopStarted = false
      }
    }

    def reportFailure(cause: Throwable): Unit =
      reporter.reportFailure(cause)
  }
}
