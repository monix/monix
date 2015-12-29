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

package monifu.internal.concurrent

import monifu.concurrent.UncaughtExceptionReporter
import monifu.internal.collection.DropHeadOnOverflowQueue

import scala.annotation.tailrec
import scala.util.control.NonFatal


private[monifu] object Trampoline {
  private[this] val state = new ThreadLocal[Local] {
    override def initialValue(): Local =
      new Local
  }

  /**
    * Schedules a new task for execution on the trampoline.
    *
    * @return true if the task was scheduled, or false if it was
    *         rejected because the queue is full.
    */
  def tryExecute(r: Runnable, reporter: UncaughtExceptionReporter): Boolean =
    state.get().tryExecute(r, reporter)

  /**
    * A simple and non-thread safe implementation of the [[Trampoline]].
    */
  final class Local {
    private[this] val queue = DropHeadOnOverflowQueue[Runnable](10000)
    private[this] var loopStarted = false

    def tryExecute(cb: Runnable, r: UncaughtExceptionReporter): Boolean = {
      if (loopStarted) {
        if (queue.isAtCapacity)
          false
        else {
          queue.offer(cb)
          true
        }
      }
      else {
        loopStarted = true
        startLoop(cb, r)
        true
      }
    }

    @tailrec
    private[this] def startLoop(cb: Runnable, r: UncaughtExceptionReporter): Unit = {
      val toRun = if (cb != null) cb
      else queue.poll()

      if (toRun != null) {
        try toRun.run()
        catch { case NonFatal(ex) => r.reportFailure(ex) }
        startLoop(null, r)
      } else {
        loopStarted = false
      }
    }
  }
}
