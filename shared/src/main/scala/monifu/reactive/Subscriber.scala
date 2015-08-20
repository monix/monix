/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive

import monifu.concurrent.Scheduler

/**
 * A `Subscriber` value is a named tuple of an observer and a scheduler,
 * whose usage is in [[Observable.create]].
 *
 * An `Observable.subscribe` takes as parameters both an [[Observer]]
 * and a [[Scheduler]] and the purpose of a `Subscriber` is convenient
 * grouping in `Observable.create`.
 *
 * A `Subscriber` value is basically an address that the data source needs
 * in order to send events.
 */
trait Subscriber[-T] {
  def observer: Observer[T]
  def scheduler: Scheduler
}

object Subscriber {
  /** Subscriber builder */
  def apply[T](observer: Observer[T], scheduler: Scheduler): Subscriber[T] =
    new Implementation[T](observer, scheduler)

  /** Utility for pattern matching */
  def unapply[T](ref: Subscriber[T]): Some[(Observer[T], Scheduler)] =
    Some((ref.observer, ref.scheduler))

  private[this] final class Implementation[-T]
      (val observer: Observer[T], val scheduler: Scheduler)
    extends Subscriber[T] {

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
  }
}
