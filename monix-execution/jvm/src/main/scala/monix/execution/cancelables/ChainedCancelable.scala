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

package monix.execution.cancelables

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import monix.execution.Cancelable
import monix.execution.Cancelable.IsDummy
import monix.execution.atomic.{ AtomicAny, PaddingStrategy }
import monix.execution.internal.exceptions.matchError

/** Represents a [[monix.execution.Cancelable]] whose underlying
  * cancelable reference can be swapped for another. It can
  * be "chained" to another `ChainedCancelable`, forwarding all
  * operations to it.
  *
  * For most purposes it works like a [[OrderedCancelable]]:
  *
  * {{{
  *   val s = ChainedCancelable()
  *   s := c1 // sets the underlying cancelable to c1
  *   s := c2 // swaps the underlying cancelable to c2
  *
  *   s.cancel() // also cancels c2
  *
  *   s := c3 // also cancels c3, because s is already canceled
  * }}}
  *
  * However it can also be linked to another `ChainedCancelable`
  * reference, forwarding all requests to it:
  *
  * {{{
  *   val source = ChainedCancelable()
  *   val child1 = ChainedCancelable()
  *   val child2 = ChainedCancelable()
  *
  *   // Hence forth forwards all operations on `child1` to `source`
  *   child1.chainTo(source)
  *
  *   // Also forwarding all `child2` operations to `source`.
  *   // This happens because `child1` was linked to `source` first
  *   // but order matters, as `child2` will be linked directly
  *   // to `source` and not to `child1`, in order for `child1` to
  *   // be garbage collected if it goes out of scope ;-)
  *   child2.chainTo(child1)
  *
  *   // Source will be updated with a new Cancelable ref
  *   child1 := Cancelable(() => println("Cancelling (1)"))
  *
  *   // Source will be updated with another Cancelable ref
  *   child2 := Cancelable(() => println("Cancelling (2)"))
  *
  *   source.cancel()
  *   //=> Cancelling (2)
  * }}}
  *
  * This implementation is a special purpose [[AssignableCancelable]],
  * much like [[StackedCancelable]], to be used in `flatMap`
  * implementations that need it.
  *
  * The problem that it solves in Monix's codebase is that various
  * `flatMap` implementations need to be memory safe.
  * By "chaining" cancelable references, we allow the garbage collector
  * to get rid of references created in a `flatMap` loop, the goal
  * being to consume a constant amount of memory. Thus this
  * implementation is used for
  * [[monix.execution.CancelableFuture CancelableFuture]].
  *
  * The implementation is also relaxed about the thread-safety of
  * the [[forwardTo]] operation, treating it like a semi-final state and
  * using Java 8 `getAndSet` platform intrinsics for performance
  * reasons.
  *
  * If unsure about what to use, then you probably don't need
  * [[ChainedCancelable]]. Use [[OrderedCancelable]] or
  * [[SingleAssignCancelable]] for most purposes.
  */
final class ChainedCancelable private (private val state: AtomicAny[AnyRef]) extends AssignableCancelable {

  import ChainedCancelable.Canceled

  private def isCanceled: Boolean = state.get() eq Canceled

  // States of `state`:
  //
  //  - null: in case this is a dummy
  //  - Cancelled: if it was cancelled
  //  - _: WeakReference[ChainedCancelable]: in case it was chained
  //  - _: Cancelable: in case it has an underlying reference

  override def cancel(): Unit = {
    while (true) {
      state.get() match {
        case Canceled => return
        case wr: ChainedCancelable.ForwardingRef =>
          wr.markCanceled()
          val cc = wr.get
          if (cc != null) cc.cancel()
          return
        case current =>
          if (state.compareAndSet(current, Canceled)) {
            current match {
              case null => ()
              case ref: Cancelable => ref.cancel()
              case other =>
                // $COVERAGE-OFF$
                matchError(other)
              // $COVERAGE-ON$
            }
            return
          }
      }
    }
  }

  private[monix] def reset(): Unit = {
    var cursor = this
    var continue = true

    while (continue) {
      cursor.state.get() match {
        case wr: ChainedCancelable.ForwardingRef =>
          wr.clearCanceled()
          val next = wr.get
          if (next == null) {
            if (cursor.state.compareAndSet(wr, null)) continue = false
          } else {
            cursor = next
          }
        case current =>
          if (cursor.state.compareAndSet(current, null)) continue = false
      }
    }
  }

  private def update(value: Cancelable): Unit = {
    val state = this.state

    while (true) {
      state.get() match {
        case Canceled =>
          value.cancel()
          return
        case wr: ChainedCancelable.ForwardingRef =>
          val cc = wr.get
          if (cc != null) cc.update(value)
          else if (wr.isCanceled) value.cancel()
          return
        case current =>
          if (state.compareAndSet(current, value)) return
      }
    }
  }

  override def `:=`(value: Cancelable): this.type = {
    update(value)
    this
  }

  /** Chains this `ChainedCancelable` to another reference,
    * such that all operations are forwarded to `other`.
    *
    * {{{
    *   val source = ChainedCancelable()
    *   val child1 = ChainedCancelable()
    *   val child2 = ChainedCancelable()
    *
    *   // Hence forth forwards all operations on `child1` to `source`
    *   child1.chainTo(source)
    *
    *   // Also forwarding all `child2` operations to `source`
    *   // (this happens because `child1` was linked to `source` first
    *   // but order matters ;-))
    *   child2.chainTo(child1)
    *
    *   // Source will be updated with a new Cancelable ref
    *   child1 := Cancelable(() => println("Cancelling (1)"))
    *
    *   // Source will be updated with another Cancelable ref
    *   child2 := Cancelable(() => println("Cancelling (2)"))
    *
    *   source.cancel()
    *   //=> Cancelling (2)
    * }}}
    */
  def forwardTo(other: ChainedCancelable): Unit = {
    // Short-circuit in case we have the same reference
    val newRoot = {
      var cursor = other
      var continue = true

      while (continue) {
        // Short-circuit if we discover a cycle
        if (cursor eq this) return
        cursor.state.get() match {
          case ref2: ChainedCancelable.ForwardingRef =>
            cursor = ref2.get
            if (cursor eq null) {
              cursor = null
              continue = false
            }
          case _ => continue = false
        }
      }
      cursor
    }

    if (newRoot != null) {
      // Using Java 8 platform intrinsics, should be faster than
      // normal getAndSet, while introducing unsafety; but in case a
      // concurrent cancellation happened, then we say sorry
      val ref = new ChainedCancelable.ForwardingRef(newRoot, newRoot.isCanceled)
      state.getAndSet(ref) match {
        case null => ()
        case Canceled => cancel()
        case _: IsDummy => ()
        case w: ChainedCancelable.ForwardingRef =>
          val cc = w.get
          if (cc != null) cc.update(newRoot)
          else if (w.isCanceled) newRoot.cancel()
        case prev: Cancelable =>
          newRoot.update(prev)
        case other =>
          // $COVERAGE-OFF$
          matchError(other)
        // $COVERAGE-ON$
      }
    }
  }
}

object ChainedCancelable {
  def apply(): ChainedCancelable =
    apply(null)

  def apply(ref: Cancelable): ChainedCancelable = {
    val state = AtomicAny.withPadding[AnyRef](ref, PaddingStrategy.LeftRight128)
    new ChainedCancelable(state)
  }

  /** Internal marker to use for chained cancellables that
    * have been cancelled.
    */
  private object Canceled

  private final class ForwardingRef(ref: ChainedCancelable, initialCanceled: Boolean)
    extends WeakReference[ChainedCancelable](ref) {
    private val canceled = new AtomicBoolean(initialCanceled)

    def isCanceled: Boolean = canceled.get()
    def markCanceled(): Unit = canceled.set(true)
    def clearCanceled(): Unit = canceled.set(false)
  }
}
