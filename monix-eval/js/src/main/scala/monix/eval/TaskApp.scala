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

package monix.eval

import monix.execution.Scheduler
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

/** Safe `App` type that runs a [[Task]] action.
  *
  * Objects inheriting from [[TaskApp]] are automatically
  * exported to JavaScript under their fully qualified name and
  * their `main` and `runc` methods as well.
  *
  * Clients should implement `runc`.
  */
trait TaskApp extends JSApp {
  @JSExport
  def runc: Task[Unit] = Task.now(())

  /** Scheduler for executing the [[Task]] action.
    * Defaults to `global`, but can be overridden.
    */
  protected val scheduler: Coeval[Scheduler] =
    Coeval.evalOnce(Scheduler.global)

  override final def main(): Unit =
    runc.runAsync(scheduler.value)
}
