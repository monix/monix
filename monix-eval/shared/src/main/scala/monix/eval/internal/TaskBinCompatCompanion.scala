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
package internal

import monix.eval.Task.{Async, Context}
import monix.execution.{Cancelable, Scheduler}

private[eval] abstract class TaskBinCompatCompanion {

  /** DEPRECATED — please switch to [[Task.cancelable[A](start* Task.cancelable]].
    *
    * The reason for the deprecation is that the `Task.async` builder
    * is now aligned to the meaning of `cats.effect.Async` and thus
    * must yield tasks that are not cancelable.
    */
  @deprecated("Renamed to Task.cancelable", since="3.0.0-M2")
  def async[A](@deprecatedName('register) start: (Scheduler, Callback[A]) => Cancelable): Task[A] = {
    // $COVERAGE-OFF$
    Task.cancelable(start)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — kept around as a package private in order to preserve
    * binary backwards compatibility.
    */
  @deprecated("Changed signature", since="3.0.0-M2")
  private[internal] def create[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] = {
    // $COVERAGE-OFF$
    TaskCreate.cancelable(register)
    // $COVERAGE-ON$
  }

  /** Internal API — deprecated due to being very error prone for usage,
    * switch to [[Task.cancelable[A](start* Task.cancelable]] instead.
    */
  @deprecated("Switch to Task.create", since = "3.0.0-RC2")
  private[monix] def unsafeCreate[A](register: (Context, Callback[A]) => Unit): Task[A] = {
    // $COVERAGE-OFF$
    Async(register)
    // $COVERAGE-ON$
  }
}
