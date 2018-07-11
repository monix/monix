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
import scala.concurrent.duration.Duration.Inf

/**
  * DEPRECATED — switch to [[SafeApp]].
  */
@deprecated("Switch to SafeApp", "3.0.0-RC2")
trait TaskApp {
  def run(args: Array[String]): Task[Unit] = {
    // $COVERAGE-OFF$
    runl(args.toList)
    // $COVERAGE-ON$
  }

  def runl(args: List[String]): Task[Unit] = {
    // $COVERAGE-OFF$
    runc
    // $COVERAGE-ON$
  }

  def runc: Task[Unit] = {
    // $COVERAGE-OFF$
    Task.now(())
    // $COVERAGE-ON$
  }

  /** Scheduler for executing the [[Task]] action.
    * Defaults to `global`, but can be overridden.
    */
  protected def scheduler: Scheduler = {
    // $COVERAGE-OFF$
    Scheduler.global
    // $COVERAGE-ON$
  }

  /** [[monix.eval.Task.Options Options]] for executing the
    * [[Task]] action. The default value is defined in
    * [[monix.eval.Task.defaultOptions defaultOptions]],
    * but can be overridden.
    */
  protected def options: Task.Options = {
    // $COVERAGE-OFF$
    Task.defaultOptions
    // $COVERAGE-ON$
  }

  final def main(args: Array[String]): Unit = {
    // $COVERAGE-OFF$
    run(args).runSyncUnsafeOpt(Inf)(scheduler, options, implicitly)
    // $COVERAGE-ON$
  }
}
