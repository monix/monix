/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.concurrent.cancelables

import monifu.concurrent.Cancelable
import collection.mutable

/**
 * Represents a composite of multiple cancelables. In case it is canceled, all
 * contained cancelables will be canceled too, e.g...
 * {{{
 *   val s = CompositeCancelable()
 *
 *   s += c1
 *   s += c2
 *   s += c3
 *
 *   // c1, c2, c3 will also be canceled
 *   s.cancel()
 * }}}
 *
 * Additionally, once canceled, on appending of new cancelable references, those
 * references will automatically get canceled too:
 * {{{
 *   val s = CompositeCancelable()
 *   s.cancel()
 *
 *   // c1 gets canceled, because s is already canceled
 *   s += c1
 *   // c2 gets canceled, because s is already canceled
 *   s += c2
 * }}}
 *
 * Adding and removing references from this composite is thread-safe.
 */
trait CompositeCancelable extends BooleanCancelable {
  /**
   * Adds a cancelable reference to this composite.
   */
  def add(s: Cancelable): Unit

  /**
   * Adds a cancelable reference to this composite.
   * This is an alias for `add()`.
   */
  final def +=(other: Cancelable): Unit = add(other)

  /**
   * Removes a cancelable reference from this composite.
   */
  def remove(s: Cancelable): Unit

  /**
   * Removes a cancelable reference from this composite.
   * This is an alias for `remove()`.
   */
  final def -=(s: Cancelable): Unit = remove(s)
}

object CompositeCancelable {
  def apply(): CompositeCancelable =
    new CompositeCancelableImpl

  def apply(head: Cancelable, tail: Cancelable*): CompositeCancelable = {
    val cs = new CompositeCancelableImpl
    cs += head; for (os <- tail) cs += os
    cs
  }

  private[this] final class CompositeCancelableImpl extends CompositeCancelable {
    private[this] var _isCanceled = false
    private[this] var subscriptions = mutable.Set.empty[Cancelable]

    def isCanceled = {
      _isCanceled
    }

    def cancel(): Unit = {
      if (!_isCanceled) {
        _isCanceled =  true
        for (s <- subscriptions) s.cancel()
        subscriptions = null
      }
    }

    def add(s: Cancelable): Unit = {
      if (_isCanceled)
        s.cancel()
      else
        subscriptions += s
    }

    def remove(s: Cancelable): Unit = {
      if (!_isCanceled)
        subscriptions -= s
    }
  }
}
