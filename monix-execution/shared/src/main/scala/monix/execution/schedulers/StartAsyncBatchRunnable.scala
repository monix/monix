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

/** Forces a real asynchronous boundary before executing the
  * given [[TrampolinedRunnable]].
  *
  * Sometimes you want to execute multiple [[TrampolinedRunnable]]
  * instances as a batch, with the functionality provided by
  * schedulers implementing [[BatchingScheduler]],
  * however you might need the very first execution to force an
  * asynchronous boundary.
  *
  * @param start is the [[TrampolinedRunnable]] instance that will get
  *        executed and that is supposed to trigger the execution of
  *        other trampolined runnables
  *
  * @param s is the scheduler that gets used for execution.
  */
final case class StartAsyncBatchRunnable(
  start: TrampolinedRunnable, s: Scheduler)
  extends Runnable with Serializable {

  def run(): Unit = {
    // Scheduler might not be an actual `BatchingScheduler`, in which case
    // we don't want to create an extra asynchronous boundary.
    s match {
      case _: BatchingScheduler =>
        s.execute(start)
      case _ =>
        start.run()
    }
  }
}
