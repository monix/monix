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

/** Cancelables represent asynchronous units of work or other things scheduled for
  * execution and whose execution can be canceled.
  *
  * One use-case is the scheduling done by [[monix.execution.Scheduler]], in which
  * the scheduling methods return a `Cancelable`, allowing the canceling of the
  * scheduling.
  *
  * Example:
  * {{{
  *   val s = ConcurrentScheduler()
  *   val task = s.scheduleRepeated(10.seconds, 50.seconds, {
  *     doSomething()
  *   })
  *
  *   // later, cancels the scheduling ...
  *   task.cancel()
  * }}}
  */
package object cancelables {
  /** DEPRECATED — renamed to [[OrderedCancelable]]. */
  @deprecated("Renamed to OrderedCancelable", "3.0.0")
  type MultiAssignmentCancelable = OrderedCancelable

  /** DEPRECATED — renamed to [[OrderedCancelable]]. */
  @deprecated("Renamed to OrderedCancelable", "3.0.0")
  val MultiAssignmentCancelable = OrderedCancelable

  /** DEPRECATED — renamed to [[SingleAssignCancelable]]. */
  @deprecated("Renamed to SingleAssignCancelable", "3.0.0")
  type SingleAssignmentCancelable = SingleAssignCancelable

  /** DEPRECATED — renamed to [[SingleAssignCancelable]]. */
  @deprecated("Renamed to SingleAssignCancelable", "3.0.0")
  val SingleAssignmentCancelable = SingleAssignCancelable
}
