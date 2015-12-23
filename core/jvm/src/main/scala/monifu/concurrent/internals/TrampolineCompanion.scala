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

package monifu.concurrent.internals

import monifu.concurrent.UncaughtExceptionReporter
import monifu.concurrent.internals.Trampoline.Local

private[concurrent] abstract class TrampolineCompanion {
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
}
