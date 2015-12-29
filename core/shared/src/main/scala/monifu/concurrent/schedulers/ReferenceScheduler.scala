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
import monifu.concurrent.cancelables.MultiAssignmentCancelable
import monifu.concurrent.{Cancelable, Scheduler}

abstract class ReferenceScheduler extends Scheduler {
  override def currentTimeMillis(): Long =
    System.currentTimeMillis()

  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val sub = MultiAssignmentCancelable()

    def loop(initialDelay: Long, delay: Long): Unit =
      sub := scheduleOnce(initialDelay, unit, new Runnable {
        def run(): Unit = {
          r.run()
          loop(delay, delay)
        }
      })

    loop(initialDelay, delay)
    sub
  }

  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val sub = MultiAssignmentCancelable()

    def loop(initialDelayMs: Long, periodMs: Long): Unit = {
      val startedAtMillis = currentTimeMillis()

      sub := scheduleOnce(initialDelayMs, TimeUnit.MILLISECONDS, new Runnable {
        def run(): Unit = {
          r.run()

          val delay = {
            val durationMillis = currentTimeMillis() - startedAtMillis
            val d = periodMs - durationMillis
            if (d >= 0) d else 0
          }

          loop(delay, periodMs)
        }
      })
    }

    val initialMs = TimeUnit.MILLISECONDS.convert(initialDelay, unit)
    val periodMs = TimeUnit.MILLISECONDS.convert(period, unit)
    
    loop(initialMs, periodMs)
    sub
  }

  /**
   * Runs a block of code in this `ExecutionContext`.
   */
  def execute(runnable: Runnable): Unit

  /**
   * Reports that an asynchronous computation failed.
   */
  def reportFailure(t: Throwable): Unit
}
