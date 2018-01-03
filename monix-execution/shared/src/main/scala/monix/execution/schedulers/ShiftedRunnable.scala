/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import monix.execution.Scheduler

/** Runnable that defers the execution of the given reference
  * with an `executeAsync`.
  *
  * This is useful for example when implementing `scheduleOnce`,
  * to introduce a boundary between the scheduling and the execution,
  * otherwise risk executing the runnable on the wrong thread-pool.
  *
  * @param r is the runnable to execute
  * @param s is the scheduler that does the execution
  */
final class ShiftedRunnable(r: Runnable, s: Scheduler)
  extends Runnable {

  override def run() =
    r match {
      case ref: TrampolinedRunnable =>
        // Cannot run directly, otherwise we risk a trampolined
        // execution on the current thread and call stack and that
        // isn't what we want
        s.execute(new StartAsyncBatchRunnable(ref, s))
      case _ =>
        s.execute(r)
    }
}
