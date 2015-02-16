/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.concurrent

import monifu.concurrent.atomic.padded.Atomic

/**
 * Represents an asynchronous computation whose execution can be canceled.
 * Used by [[monifu.concurrent.Scheduler]] giving you the ability to cancel scheduled units of work.
 *
 * It is equivalent to `java.io.Closeable`, but without the I/O focus, or to `IDisposable` in Microsoft .NET,
 * or to `akka.actor.Cancellable`.
 */
trait Cancelable {
  /**
   * Cancels the unit of work represented by this reference.
   *
   * Guaranteed idempotency - calling it multiple times should have the
   * same side-effect as calling it only a single time. Implementations
   * of this method should also be thread-safe.
   *
   * @return true if cancellation happened, or false if another execution
   *         happened previously or concurrently.
   */
  def cancel(): Boolean
}

object Cancelable {
  def apply(callback: => Unit): Cancelable =
    new Cancelable {
      private[this] val _isCanceled = Atomic(false)

      def cancel(): Boolean = {
        if (_isCanceled.compareAndSet(expect=false, update=true)) {
          callback
          true
        }
        else
          false
      }        
    }

  def apply(): Cancelable =
    new Cancelable {
      private[this] val _isCanceled = Atomic(false)

      def cancel(): Boolean =
        _isCanceled.compareAndSet(expect = false, update = true)
    }
}
