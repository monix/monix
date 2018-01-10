/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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
  * or the `initialValue` (as the seed) in case no value has yet been emitted, then continuing
  * to emit events subsequent to the time of invocation via an underlying [[ConcurrentSubject]].
  *
  * @see [[ConcurrentSubject]]
  */
final class Var[A] private (
  initialValue: A
)(implicit scheduler: Scheduler)
    extends Observable[A] { self =>

  private[this] var value: A   = initialValue
  private[this] val underlying = ConcurrentSubject.behavior(initialValue, OverflowStrategy.Unbounded)

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
  def apply[A](
    initialValue: A
  )(implicit scheduler: Scheduler): Var[A] = {
    new Var[A](initialValue)
  }
}
