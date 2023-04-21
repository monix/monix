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

package monix.execution.rstreams

import monix.execution.atomic.AtomicAny
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
final class SingleAssignSubscription private () extends Subscription {

  import SingleAssignSubscription.State
  import SingleAssignSubscription.State._

  private[this] val state = AtomicAny(Empty: State)

  def :=(s: org.reactivestreams.Subscription): Unit = set(s)

  def set(s: org.reactivestreams.Subscription): Unit = {
    val current = state.get()

    current match {
      case Empty =>
        if (!state.compareAndSet(current, WithSubscription(s)))
          set(s) // retry

      case EmptyRequest(n) =>
        if (!state.compareAndSet(current, WithSubscription(s)))
          set(s) // retry
        else
          s.request(n)

      case EmptyCanceled =>
        if (!state.compareAndSet(current, Canceled))
          set(s) // retry
        else
          s.cancel()

      case WithSubscription(_) | Canceled =>
        // Spec 205: A Subscriber MUST call Subscription.cancel() on the
        // given Subscription after an onSubscribe signal if it already
        // has an active Subscription
        s.cancel()
    }
  }

  def cancel(): Unit = {
    @tailrec
    def loop(): Unit = {
      val current = state.get()

      current match {
        case Empty =>
          if (!state.compareAndSet(current, EmptyCanceled))
            loop() // retry

        case EmptyRequest(_) =>
          if (!state.compareAndSet(current, EmptyCanceled))
            loop() // retry

        case WithSubscription(s) =>
          if (!state.compareAndSet(current, Canceled))
            loop() // retry
          else
            s.cancel()

        case EmptyCanceled | Canceled =>
          () // do nothing
      }
    }
    loop()
  }

  def request(n: Long): Unit = {
    @tailrec
    def loop(): Unit = {
      val current = state.get()

      current match {
        case Empty =>
          if (!state.compareAndSet(current, EmptyRequest(n)))
            loop() // retry

        case EmptyRequest(previous) =>
          if (!state.compareAndSet(current, EmptyRequest(previous + n)))
            loop() // retry

        case WithSubscription(s) =>
          s.request(n)

        case EmptyCanceled | Canceled =>
          ()
      }
    }
    loop()
  }
}

object SingleAssignSubscription {
  /** Builder for [[SingleAssignSubscription]] */
  def apply(): SingleAssignSubscription =
    new SingleAssignSubscription

  /** Internal state kept in an atomic reference. */
  private sealed abstract class State

  private object State {
    /** Subscription hasn't been initialized. */
    case object Empty extends State

    /** Subscription hasn't been initialized, but at least
      * a request was received from subscriber.
      */
    case class EmptyRequest(nr: Long) extends State

    /** Subscription hasn't been initialized, but the
      * subscriber has already cancelled.
      */
    case object EmptyCanceled extends State

    /** Has an active subscription. */
    case class WithSubscription(s: org.reactivestreams.Subscription) extends State

    /** Subscription was cancelled. */
    case object Canceled extends State
  }
}
