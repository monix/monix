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

package monix.execution
package schedulers

/** Defines extension methods for [[Scheduler]] meant for
  * executing runnables.
  *
  * NOTE: these extension methods are only defined as macros
  * for Scala < 2.12, because in Scala 2.12 we simply rely on
  * its SAM support.
  */
private[execution] trait ExecuteExtensions extends Any {
  def source: Scheduler

  /** Schedules the given callback for asynchronous
    * execution in the thread-pool.
    *
    * On Scala < 2.12 it is described as a macro, so it
    * has zero overhead, being perfectly equivalent with
    * `execute(new Runnable { ... })`.
    *
    * On Scala 2.12 because of the Java 8 SAM types integration,
    * this extension macro is replaced with a method that takes
    * a plain `Runnable` as parameter.
    *
    * @param cb the callback to execute asynchronously
    */
  def executeAsync(cb: Runnable): Unit =
    source.execute(cb)

  /** Schedules the given callback for asynchronous
    * execution in the thread-pool, but also indicates the
    * start of a
    * [[monix.execution.schedulers.TrampolinedRunnable thread-local trampoline]]
    * in case the scheduler is a
    * [[monix.execution.schedulers.BatchingScheduler BatchingScheduler]].
    *
    * This utility is provided as an optimization. If you don't understand
    * what this does, then don't worry about it.
    *
    * On Scala < 2.12 it is described as a macro, so it
    * has zero overhead. On Scala 2.12 because of the Java 8 SAM
    * types integration, this extension macro is replaced with a
    * method that takes a plain `TrampolinedRunnable` as parameter.
    *
    * @param cb the callback to execute asynchronously
    */
  def executeAsyncBatch(cb: TrampolinedRunnable): Unit = {
    val r = StartAsyncBatchRunnable(cb, source)
    source.execute(r)
  }

  /** Schedules the given callback for immediate execution as a
    * [[monix.execution.schedulers.TrampolinedRunnable TrampolinedRunnable]].
    * Depending on the execution context, it might
    * get executed on the current thread by using an internal
    * trampoline, so it is still safe from stack-overflow exceptions.
    *
    * On Scala < 2.12 it is described as a macro, so it
    * has zero overhead, being perfectly equivalent with
    * `execute(new TrampolinedRunnable { ... })`.
    *
    * On Scala 2.12 because of the Java 8 SAM types integration,
    * this extension macro is replaced with a method that takes
    * a plain `TrampolinedRunnable` as parameter.
    *
    * @param cb the callback to execute asynchronously
    */
  def executeTrampolined(cb: TrampolinedRunnable): Unit =
    source.execute(cb)
}
