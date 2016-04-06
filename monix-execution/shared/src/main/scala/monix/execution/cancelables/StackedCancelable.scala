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

import monix.execution.Cancelable
import org.sincron.atomic.{Atomic, PaddingStrategy}

import scala.annotation.tailrec

final class StackedCancelable private (initial: Cancelable)
  extends BooleanCancelable {

  private def underlying: List[Cancelable] = state.get
  private[this] val state = {
    val ref = if (initial != null) initial :: Nil else Nil
    Atomic.withPadding(ref, PaddingStrategy.LeftRight128)
  }

  override def isCanceled: Boolean =
    state.get == null

  override def cancel(): Unit = {
    // Using getAndSet, which on Java 8 should be faster than
    // a compare-and-set.
    val oldState = state.getAndSet(null)
    if (oldState ne null) oldState.foreach(_.cancel())
  }

  @tailrec def popAndCollapse(value: StackedCancelable): Cancelable = {
    val other = value.underlying
    if (other == null) {
      this.cancel()
      Cancelable.empty
    }
    else state.get match {
      case null =>
        value.cancel()
        Cancelable.empty
      case Nil =>
        if (!state.compareAndSet(Nil, other))
          popAndCollapse(value)
        else
          Cancelable.empty
      case ref @ (head :: tail) =>
        if (!state.compareAndSet(ref, other ::: tail))
          popAndCollapse(value)
        else
          head
    }
  }

  @tailrec def popAndPush(value: Cancelable): Cancelable = {
    state.get match {
      case null =>
        value.cancel()
        Cancelable.empty
      case Nil =>
        if (!state.compareAndSet(Nil, value :: Nil))
          popAndPush(value)
        else
          Cancelable.empty
      case ref @ (head :: tail) =>
        if (!state.compareAndSet(ref, value :: tail))
          popAndPush(value) // retry
        else
          head
    }
  }

  @tailrec def push(value: Cancelable): Unit = {
    state.get match {
      case null =>
        value.cancel()
      case stack =>
        if (!state.compareAndSet(stack, value :: stack))
          push(value) // retry
    }
  }

  @tailrec def pop(): Cancelable = {
    state.get match {
      case null => Cancelable.empty
      case Nil => Cancelable.empty
      case ref @ (head :: tail) =>
        if (!state.compareAndSet(ref, tail))
          pop()
        else
          head
    }
  }
}

object StackedCancelable {
  def apply(): StackedCancelable =
    new StackedCancelable(null)

  def apply(s: Cancelable): StackedCancelable =
    new StackedCancelable(s)
}