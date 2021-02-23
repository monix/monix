/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.execution.atomic.{AtomicAny, PaddingStrategy}
import scala.annotation.tailrec

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
final class CompositeCancelable private (stateRef: AtomicAny[CompositeCancelable.State]) extends BooleanCancelable {
  self =>

  import CompositeCancelable.{Active, Cancelled}

  override def isCanceled: Boolean =
    stateRef.get() eq Cancelled

  @tailrec override def cancel(): Unit =
    stateRef.get() match {
      case Cancelled => ()
      case current @ Active(set) =>
        if (stateRef.compareAndSet(current, Cancelled))
          Cancelable.cancelAll(set)
        else {
          // $COVERAGE-OFF$
          cancel()
          // $COVERAGE-ON$
        }
    }

  /** $addOp
    *
    * Alias for [[add]].
    */
  def +=(other: Cancelable): this.type =
    add(other)

  /** $addOp */
  @tailrec def add(other: Cancelable): this.type =
    stateRef.get() match {
      case Cancelled =>
        other.cancel()
        this
      case current @ Active(set) =>
        if (stateRef.compareAndSet(current, Active(set + other))) {
          this
        } else {
          // $COVERAGE-OFF$
          add(other)
          // $COVERAGE-ON$
        }
    }

  /** $addAllOp
    *
    * Alias for [[addAll]].
    */
  def ++=(that: Iterable[Cancelable]): this.type =
    addAll(that)

  /** $addAllOp */
  def addAll(that: Iterable[Cancelable]): this.type = {
    @tailrec def loop(that: Iterable[Cancelable]): this.type =
      stateRef.get() match {
        case Cancelled =>
          Cancelable.cancelAll(that)
          this
        case current @ Active(set) =>
          if (stateRef.compareAndSet(current, Active(set ++ that))) {
            this
          } else {
            // $COVERAGE-OFF$
            loop(that)
            // $COVERAGE-ON$
          }
      }

    loop(that.toSeq)
  }

  /** $removeOp
    *
    * Alias for [[remove]].
    */
  def -=(s: Cancelable): this.type = remove(s)

  /** $removeOp */
  @tailrec def remove(s: Cancelable): this.type =
    stateRef.get() match {
      case Cancelled => this
      case current @ Active(set) =>
        if (stateRef.compareAndSet(current, Active(set - s))) {
          this
        } else {
          // $COVERAGE-OFF$
          remove(s)
          // $COVERAGE-ON$
        }
    }

  /** $removeAllOp
    *
    * Alias for [[removeAll]].
    */
  def --=(that: Iterable[Cancelable]): this.type =
    removeAll(that)

  /** $removeAllOp */
  def removeAll(that: Iterable[Cancelable]): this.type = {
    @tailrec def loop(that: Iterable[Cancelable]): this.type =
      stateRef.get() match {
        case Cancelled => this
        case current @ Active(set) =>
          if (stateRef.compareAndSet(current, Active(set -- that))) {
            this
          } else {
            // $COVERAGE-OFF$
            loop(that)
            // $COVERAGE-ON$
          }
      }

    loop(that.toSeq)
  }

  /** Resets this composite to an empty state, if not canceled,
    * otherwise leaves it in the canceled state.
    */
  @tailrec def reset(): this.type =
    stateRef.get() match {
      case Cancelled => this
      case current @ Active(_) =>
        if (stateRef.compareAndSet(current, Active(Set.empty))) {
          this
        } else {
          // $COVERAGE-OFF$
          reset() // retry
          // $COVERAGE-ON$
        }
    }

  /** Replaces the underlying set of cancelables with a new one,
    * returning the old set just before the substitution happened.
    */
  def getAndSet(that: Iterable[Cancelable]): Set[Cancelable] = {
    @tailrec def loop(that: Set[Cancelable]): Set[Cancelable] =
      stateRef.get() match {
        case Cancelled =>
          Cancelable.cancelAll(that)
          Set.empty
        case current @ Active(set) =>
          if (stateRef.compareAndSet(current, Active(that))) {
            set
          } else {
            // $COVERAGE-OFF$
            loop(that)
            // $COVERAGE-ON$
          }
      }

    that match {
      case ref: Set[_] =>
        loop(ref.asInstanceOf[Set[Cancelable]])
      case _ =>
        loop(that.toSet[Cancelable])
    }
  }
}

object CompositeCancelable {
  /** Builder for [[CompositeCancelable]]. */
  def apply(initial: Cancelable*): CompositeCancelable =
    withPadding(Set(initial: _*), PaddingStrategy.LeftRight128)

  /** Builder for [[CompositeCancelable]]. */
  def fromSet(initial: Set[Cancelable]): CompositeCancelable =
    withPadding(initial, PaddingStrategy.LeftRight128)

  /** Builder for [[CompositeCancelable]] that can specify a
    * [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
    * to the underlying atomic reference.
    */
  def withPadding(ps: PaddingStrategy): CompositeCancelable =
    withPadding(Set.empty, ps)

  /** Builder for [[CompositeCancelable]] that can specify a
    * [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
    * to the underlying atomic reference.
    */
  def withPadding(initial: Set[Cancelable], ps: PaddingStrategy): CompositeCancelable = {
    val ref: AtomicAny[State] = AtomicAny.withPadding(Active(initial), ps)
    new CompositeCancelable(ref)
  }

  private sealed abstract class State
  private final case class Active(set: Set[Cancelable]) extends State
  private case object Cancelled extends State
}
