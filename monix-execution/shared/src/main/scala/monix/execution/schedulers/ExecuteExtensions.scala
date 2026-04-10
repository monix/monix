/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
  */
private[execution] trait ExecuteExtensions extends Any {
  def source: Scheduler

  @deprecated("Use `execute` directly, since Scala 2.11 support has been dropped", "3.4.0")
  def executeAsync(cb: Runnable): Unit =
    source.execute(cb)

  @deprecated("Extension methods are now implemented on `Scheduler` directly", "3.4.0")
  def executeAsyncBatch(cb: TrampolinedRunnable): Unit =
    source.executeAsyncBatch(cb)

  @deprecated("Extension methods are now implemented on `Scheduler` directly", "3.4.0")
  def executeTrampolined(cb: TrampolinedRunnable): Unit =
    source.executeTrampolined(cb)
}
