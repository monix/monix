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

package monix.execution

import monix.execution.atomic.AtomicAny

/** Represents a one-time idempotent action that can be used
  * to cancel async computations, or to release resources that
  * active data sources are holding.
  *
  * It is equivalent to `java.io.Closeable`, but without the I/O focus, or
  * to `IDisposable` in Microsoft .NET, or to `akka.actor.Cancellable`.
  */
trait Cancelable extends Any with Serializable {
  /** Cancels the unit of work represented by this reference.
    *
    * Guaranteed idempotency - calling it multiple times should have the
    * same side-effect as calling it only once. Implementations
    * of this method should also be thread-safe.
    */
  def cancel(): Unit
}

object Cancelable {
  /** Builds a [[monix.execution.Cancelable Cancelable]]. */
  def apply(): Cancelable =
    empty

  /** Builds a [[monix.execution.Cancelable Cancelable]] that executes the given
    * `callback` when calling [[Cancelable.cancel cancel]].
    */
  def apply(callback: () => Unit): Cancelable =
    new CancelableTask(callback)

  val empty: Cancelable =
    new Cancelable {
      def cancel() = ()
      override def toString = "monix.execution.Cancelable.empty"
    }

  private final class CancelableTask(cb: () => Unit)
    extends Cancelable {

    private[this] val callbackRef = AtomicAny(cb)

    def cancel(): Unit = {
      // Setting the callback to null with a `getAndSet` is solving
      // two problems: `cancel` is idempotent, plus we allow the garbage
      // collector to collect the task.
      val callback = callbackRef.getAndSet(null)
      if (callback != null) callback()
    }
  }
}
