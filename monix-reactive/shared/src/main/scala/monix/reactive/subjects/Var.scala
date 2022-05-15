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

package monix.reactive.subjects

import monix.execution.{ Ack, Cancelable, Scheduler }
import monix.reactive.{ Observable, OverflowStrategy }
import monix.reactive.observers.Subscriber

/** `Var` when subscribed, will emit the most recently emitted item by the source,
  * or the `initial` (as the seed) in case no value has yet been emitted, then continuing
  * to emit events subsequent to the time of invocation via an underlying [[ConcurrentSubject]].
  * Note that this data type is equivalent to a `ConcurrentSubject.behavior(Unbounded)` with
  * additional functionality to expose the current value for immediate usage.
  *
  * Sample usage:
  *
  * {{{
  *   import monix.reactive._
  *   import monix.reactive.subjects.Var
  *   import monix.execution.Scheduler.Implicits.global
  *
  *   val a = Var(0)
  *   val b = Var(0)
  *
  *   // Sum that gets re-calculated "reactively"
  *   val sum = Observable.combineLatestMap2(a, b)(_ + _)
  *
  *   // Subscribes for updates
  *   sum.dump("Sum").subscribe()
  *
  *   a := 4
  *   // 0: Sum --> 4
  *   b := 5
  *   // 1: Sum --> 9
  *   a := 10
  *   // 0: Sum --> 15
  * }}}
  *
  * @see [[ConcurrentSubject]]
  */
final class Var[A] private (initial: A)(implicit s: Scheduler) extends Observable[A] { self =>

  private[this] var value: A = initial
  private[this] val underlying = ConcurrentSubject.behavior(initial, OverflowStrategy.Unbounded)

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable =
    underlying.unsafeSubscribeFn(subscriber)

  /**
    * @return the current value of this `Var`
    */
  def apply(): A =
    self.synchronized(value)

  /** Updates the current value of this `Var`.
    *
    * Subscribers to this `Var` will be notified of the new value.
    *
    * @return Acknowledgment of update to the underlying [[ConcurrentSubject]].
    */
  def `:=`(update: A): Ack =
    self.synchronized {
      value = update
      underlying.onNext(update)
    }
}

object Var {
  /** Builder for [[Var]] */
  def apply[A](initial: A)(implicit s: Scheduler): Var[A] = {
    new Var[A](initial)
  }
}
