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

package monix.execution.rstreams

import org.sincron.atomic.Atomic
import scala.annotation.tailrec

/** Represents a `org.reactivestreams.Subscription` that can be assigned
  * only once to another subscription reference.
  *
  * If the assignment happens after this subscription has been canceled, then on
  * assignment the reference will get canceled too. If the assignment
  * after `request(n)` has been called on this subscription, then
  * `request(n)` will get called immediately on the assigned reference as well.
  *
  * Useful in case you need a thread-safe forward reference.
  */
final class SingleAssignmentSubscription private ()
  extends Subscription {

  import SingleAssignmentSubscription.State
  import SingleAssignmentSubscription.State._

  private[this] val state = Atomic(Empty : State)

  def :=(s: org.reactivestreams.Subscription): Unit = set(s)

  def set(s: org.reactivestreams.Subscription): Unit = {
    val current = state.get

    current match {
      case Empty =>
        if (!state.compareAndSet(current, WithSubscription(s)))
          set(s) // retry

      case EmptyRequest(n) =>
        if (!state.compareAndSet(current, WithSubscription(s)))
          set(s) // retry
        else
          s.request(n)

      case WithSubscription(_) =>
        throw new IllegalArgumentException(
          "SingleAssignmentSubscription as already initialized")

      case EmptyCanceled =>
        if (!state.compareAndSet(current, Canceled))
          set(s) // retry
        else
          s.cancel()

      case Canceled =>
        throw new IllegalArgumentException(
          "SingleAssignmentSubscription as already initialized")
    }
  }

  @tailrec
  def cancel(): Unit = {
    val current = state.get

    current match {
      case Empty =>
        if (!state.compareAndSet(current, EmptyCanceled))
          cancel() // retry

      case EmptyRequest(_) =>
        if (!state.compareAndSet(current, EmptyCanceled))
          cancel() // retry

      case WithSubscription(s) =>
        if (!state.compareAndSet(current, Canceled))
          cancel() // retry
        else
          s.cancel()

      case EmptyCanceled | Canceled =>
        () // do nothing
    }
  }

  @tailrec
  def request(n: Long): Unit = {
    val current = state.get

    current match {
      case Empty =>
        if (!state.compareAndSet(current, EmptyRequest(n)))
          request(n) // retry

      case EmptyRequest(previous) =>
        if (!state.compareAndSet(current, EmptyRequest(previous + n)))
          request(n) // retry

      case WithSubscription(s) =>
        s.request(n)

      case EmptyCanceled | Canceled =>
        ()
    }
  }
}

object SingleAssignmentSubscription {
  /** Builder for [[SingleAssignmentSubscription]] */
  def apply(): SingleAssignmentSubscription =
    new SingleAssignmentSubscription

  private sealed trait State

  private object State {
    case object Empty extends State
    case class EmptyRequest(nr: Long) extends State
    case object EmptyCanceled extends State
    case class WithSubscription(s: org.reactivestreams.Subscription) extends State
    case object Canceled extends State
  }
}
