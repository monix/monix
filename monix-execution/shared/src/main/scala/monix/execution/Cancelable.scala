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
 *
 */

package monix.execution

import org.sincron.atomic.Atomic

/** Represents an asynchronous computation whose execution can be canceled.
  *
  * It is equivalent to `java.io.Closeable`, but without the I/O focus, or
  * to `IDisposable` in Microsoft .NET, or to `akka.actor.Cancellable`.
  */
trait Cancelable {
  /** Cancels the unit of work represented by this reference.
    *
    * Guaranteed idempotency - calling it multiple times should have the
    * same side-effect as calling it only once. Implementations
    * of this method should also be thread-safe.
    */
  def cancel(): Unit
}

object Cancelable {
  /** Builds a [[Cancelable]]. */
  def apply(): Cancelable =
    empty

  /** Builds a [[Cancelable]] that executes the given
    * `callback` when calling [[Cancelable.cancel cancel]].
    */
  def apply(callback: => Unit): Cancelable =
    new Cancelable {
      private[this] val _isCanceled = Atomic(false)

      def cancel(): Unit =
        if (!_isCanceled.getAndSet(true)) {
          callback
        }
    }

  val empty: Cancelable =
    new Cancelable {
      def cancel() = ()
    }
}
