/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.concurrent.schedulers

import monifu.concurrent.schedulers.Timer.{clearTimeout, setTimeout}
import monifu.concurrent.{Cancelable, Scheduler, UncaughtExceptionReporter}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


final class TrampolineScheduler private
    (reporter: UncaughtExceptionReporter)
  extends ReferenceScheduler {

  private[this] val immediateQueue = mutable.Queue.empty[Runnable]
  private[this] var withinLoop = false

  def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable = {
    val task = setTimeout(initialDelay.toMillis, r, reporter)
    Cancelable(clearTimeout(task))
  }

  def execute(runnable: Runnable): Unit = {
    immediateQueue.enqueue(runnable)
    if (!withinLoop) {
      withinLoop = true
      try immediateLoop() finally {
        withinLoop = false
      }
    }
  }

  @tailrec
  private[this] def immediateLoop(): Unit = {
    if (immediateQueue.nonEmpty) {
      val task = immediateQueue.dequeue()

      try {
        task.run()
      }
      catch {
        case NonFatal(ex) =>
          reportFailure(ex)
      }

      immediateLoop()
    }
  }

  def reportFailure(t: Throwable): Unit =
    reporter.reportFailure(t)
}

object TrampolineScheduler {
  def apply(reporter: UncaughtExceptionReporter): TrampolineScheduler =
    new TrampolineScheduler(reporter)
}
