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
import scala.collection.{GenTraversableOnce, mutable}

/** Represents a composite of multiple cancelables. In case it is canceled, all
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
  *
  * @define addOp Adds a cancelable reference to this composite.
  *
  *         If this composite is already canceled, then the given
  *         reference will be canceled as well.
  *
  * @define addAllOp Adds a collection of cancelables to this composite.
  *
  *         If this composite is already canceled, then all the
  *         cancelables in the given collection will get canceled
  *         as well.
  *
  * @define removeOp Removes a cancelable reference from this composite.
  *
  *         By removing references from the composite, we ensure that
  *         the removed references don't get canceled when the composite
  *         gets canceled. Also useful for garbage collecting purposes.
  *
  * @define removeAllOp Removes a collection of cancelables from this composite.
  *
  *         By removing references from the composite, we ensure that
  *         the removed references don't get canceled when the composite
  *         gets canceled. Also useful for garbage collecting purposes.
  */
final class CompositeCancelable private (cancelables: TraversableOnce[Cancelable])
  extends BooleanCancelable { self =>

  private[this] var isCanceledRef = false
  private[this] var subscriptions = self.synchronized {
    mutable.HashSet.empty[Cancelable] ++ cancelables
  }

  override def isCanceled: Boolean =
    isCanceledRef

  override def cancel(): Unit = self.synchronized {
    if (!isCanceledRef) {
      isCanceledRef = true

      val cursor = subscriptions.iterator
      while (cursor.hasNext) { cursor.next().cancel() }
      subscriptions = null // GC relief
    }
  }

  /** $addOp
    *
    * Alias for [[add]].
    */
  def +=(other: Cancelable): this.type =
    add(other)

  /** $addOp */
  def add(other: Cancelable): this.type = self.synchronized {
    if (isCanceledRef) other.cancel() else subscriptions.add(other)
    this
  }

  /** $addAllOp
    *
    * Alias for [[addAll]].
    */
  def ++=(that: GenTraversableOnce[Cancelable]): this.type =
    addAll(that)

  /** $addAllOp */
  def addAll(that: GenTraversableOnce[Cancelable]): this.type =
    self.synchronized {
      if (!isCanceledRef) {
        subscriptions ++= that.seq
      } else {
        // Canceling everything
        val cursor = that.toIterator
        while (cursor.hasNext) { cursor.next().cancel() }
      }

      this
    }

  /** $removeOp
    *
    * Alias for [[remove]].
    */
  def -=(s: Cancelable): this.type = remove(s)

  /** $removeOp */
  def remove(s: Cancelable): this.type = self.synchronized {
    if (!isCanceledRef) subscriptions.add(s)
    this
  }

  /** $removeAllOp
    *
    * Alias for [[removeAll]].
    */
  def --=(that: GenTraversableOnce[Cancelable]): this.type =
    removeAll(that)

  /** $removeAllOp */
  def removeAll(that: GenTraversableOnce[Cancelable]): this.type =
    self.synchronized {
      if (!isCanceledRef && that.nonEmpty) subscriptions ++= that.seq
      this
    }

  /** Resets this composite to an empty state, if not canceled,
    * otherwise leaves it in the canceled state.
    */
  def reset(): this.type = self.synchronized {
    if (!isCanceledRef) subscriptions.clear()
    this
  }
}

object CompositeCancelable {
  /** Builder for [[CompositeCancelable]]. */
  def apply(initial: Cancelable*): CompositeCancelable = {
    if (initial.nonEmpty)
      new CompositeCancelable(initial)
    else
      new CompositeCancelable(Nil)
  }
}
