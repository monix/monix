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

package monix.execution.schedulers

import java.util.concurrent.TimeUnit
import monix.execution.schedulers.Timer.{clearTimeout, setTimeout}
import monix.execution.{Cancelable, UncaughtExceptionReporter}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.TimeUnit
import scala.util.control.NonFatal

private[schedulers] final class TrampolineScheduler private (reporter: UncaughtExceptionReporter)
  extends ReferenceScheduler {

  private[this] val immediateQueue = mutable.Queue.empty[Runnable]
  private[this] var withinLoop = false

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val millis = {
      val v = TimeUnit.MILLISECONDS.convert(initialDelay, unit)
      if (v < 0) 0L else v
    }

    val task = setTimeout(millis, r, reporter)
    Cancelable(() => clearTimeout(task))
  }

  override def execute(runnable: Runnable): Unit = {
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

  override def reportFailure(t: Throwable): Unit =
    reporter.reportFailure(t)
}

private[schedulers] object TrampolineScheduler {
  def apply(reporter: UncaughtExceptionReporter): TrampolineScheduler =
    new TrampolineScheduler(reporter)
}
