/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.reactive.internal.util

import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.TracingScheduler

private[reactive] object TaskRun {
  /**
    * Builds a [[monix.eval.Task.Options]] reference, for executing
    * tasks in the context of `Observable`.
    */
  def options(implicit s: Scheduler): Task.Options =
    s match {
      case _: TracingScheduler => optionsWithLocalsRef
      case _ => Task.defaultOptions
    }

  /**
    * Options value for execution tasks with
    * "local context propagation" enabled.
    */
  val optionsWithLocalsRef = Task.defaultOptions.enableLocalContextPropagation
}
