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

package monix.eval

import monix.execution.Scheduler
import scala.scalajs.js.annotation.JSExport

/** Safe `App` type that runs a [[Task]] action.
  *
  * Objects inheriting from [[TaskApp]] are automatically
  * exported to JavaScript under their fully qualified name and
  * their `main` and `runc` methods as well.
  *
  * Clients should implement `runc`.
  */
trait TaskApp {
  @JSExport
  def run(args: Array[String]): Task[Unit] =
    runl(args.toList)

  @JSExport
  def runl(args: List[String]): Task[Unit] =
    runc

  @JSExport
  def runc: Task[Unit] =
    Task.now(())

  /** Scheduler for executing the [[Task]] action.
    * Defaults to `global`, but can be overridden.
    */
  protected val scheduler: Coeval[Scheduler] =
    Coeval.evalOnce(Scheduler.global)

  /** [[monix.eval.Task.Options Options]] for executing the
    * [[Task]] action. The default value is defined in
    * [[monix.eval.Task.defaultOptions defaultOptions]],
    * but can be overridden.
    */
  protected val options: Coeval[Task.Options] =
    Coeval.evalOnce(Task.defaultOptions)

  @JSExport
  final def main(args: Array[String]): Unit = {
    run(args).runAsyncOpt(scheduler.value, options.value)
  }
}
