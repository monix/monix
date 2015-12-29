/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.concurrent.schedulers

import java.util.concurrent.TimeUnit
import monifu.concurrent.Scheduler.{Platform, Environment}
import monifu.concurrent.schedulers.Timer.{clearTimeout, setTimeout}
import monifu.concurrent.{Cancelable, UncaughtExceptionReporter}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import scala.util.control.NonFatal


final class TrampolineScheduler private (reporter: UncaughtExceptionReporter)
  extends ReferenceScheduler {

  private[this] val immediateQueue = mutable.Queue.empty[Runnable]
  private[this] var withinLoop = false

  override def scheduleOnce(r: Runnable): Cancelable = {
    execute(r)
    Cancelable.empty
  }

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable) = {
    val millis = {
      val v = TimeUnit.MILLISECONDS.convert(initialDelay, unit)
      if (v < 0) 0L else v
    }

    val task = setTimeout(millis, r, reporter)
    Cancelable(clearTimeout(task))
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

  override val env =
    Environment(256, Platform.JS)
}

object TrampolineScheduler {
  def apply(reporter: UncaughtExceptionReporter): TrampolineScheduler =
    new TrampolineScheduler(reporter)
}
