/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.observers

import monifu.concurrent.Scheduler
import monifu.reactive.Subscriber

/**
 * A `SynchronousSubscriber` is a [[Subscriber]] whose observer
 * signals demand synchronously (i.e. the upstream observable doesn't need to
 * wait on a `Future` in order to decide whether to send the next event
 * or not).
 */
trait SynchronousSubscriber[-T] extends Subscriber[T] {
  def observer: SynchronousObserver[T]
}

object SynchronousSubscriber {
  /** Subscriber builder */
  def apply[T](observer: SynchronousObserver[T], scheduler: Scheduler): SynchronousSubscriber[T] =
    new Implementation[T](observer, scheduler)

  /** Utility for pattern matching */
  def unapply[T](ref: SynchronousSubscriber[T]): Some[(SynchronousObserver[T], Scheduler)] =
    Some((ref.observer, ref.scheduler))

  private[this] final class Implementation[-T]
      (val observer: SynchronousObserver[T], val scheduler: Scheduler)
    extends SynchronousSubscriber[T] {

    require(observer != null, "Observer should not be null")
    require(scheduler != null, "Scheduler should not be null")

    override def equals(other: Any): Boolean = other match {
      case that: Implementation[_] =>
        observer == that.observer &&
          scheduler == that.scheduler
      case _ =>
        false
    }

    override val hashCode: Int = {
      31 * observer.hashCode() + scheduler.hashCode()
    }
  }}