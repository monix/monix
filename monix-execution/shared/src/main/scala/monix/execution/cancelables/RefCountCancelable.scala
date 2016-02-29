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

package monix.execution.cancelables

import org.sincron.atomic.Atomic
import monix.execution.Cancelable
import scala.annotation.tailrec

/** Represents a `Cancelable` that only executes the canceling logic when all
  * dependent cancelable objects have been canceled.
  *
  * The given callback gets called after our `RefCountCancelable` is canceled
  * and after all dependent cancelables have been canceled as well.
  */
final class RefCountCancelable private (onCancel: () => Unit) extends BooleanCancelable {
  def isCanceled: Boolean =
    state.get.isCanceled

  /** Acquires a new [[Cancelable]]. */
  @tailrec
  def acquire(): Cancelable = {
    val oldState = state.get
    if (oldState.isCanceled)
      Cancelable.empty
    else if (!state.compareAndSet(oldState, oldState.copy(activeCounter = oldState.activeCounter + 1)))
      acquire()
    else
      Cancelable { () =>
        val newState = state.transformAndGet(s => s.copy(activeCounter = s.activeCounter - 1))
        if (newState.activeCounter == 0 && newState.isCanceled)
          onCancel()
      }
  }

  override def cancel(): Unit = {
    val oldState = state.get
    if (!oldState.isCanceled)
      if (!state.compareAndSet(oldState, oldState.copy(isCanceled = true)))
        cancel()
      else if (oldState.activeCounter == 0)
        onCancel()
  }

  private[this] val state = Atomic(State(isCanceled = false, activeCounter = 0))
  private[this] case class State(
    isCanceled: Boolean,
    activeCounter: Int
  )
}

object RefCountCancelable {
  def apply(onCancel: => Unit): RefCountCancelable =
    new RefCountCancelable(() => onCancel)
}
