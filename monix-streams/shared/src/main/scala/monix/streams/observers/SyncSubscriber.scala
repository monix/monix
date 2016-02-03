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

package monix.streams.observers

import monix.execution.Scheduler
import monix.streams.{Subscriber, Ack}

/** A `SyncSubscriber` is a [[Subscriber]] whose `onNext` signal
  * is synchronous (i.e. the upstream observable doesn't need to
  * wait on a `Future` in order to decide whether to send the next event
  * or not).
  */
trait SyncSubscriber[-T] extends Subscriber[T]
  with SyncObserver[T]

object SyncSubscriber {
  /** Subscriber builder */
  def apply[T](observer: SyncObserver[T], scheduler: Scheduler): SyncSubscriber[T] =
    observer match {
      case ref: SyncSubscriber[_] if ref.scheduler == scheduler =>
        ref.asInstanceOf[SyncSubscriber[T]]
      case _ =>
        new Implementation[T](observer, scheduler)
    }

  private[this] final class Implementation[-T]
      (observer: SyncObserver[T], val scheduler: Scheduler)
    extends SyncSubscriber[T] {

    require(observer != null, "Observer should not be null")
    require(scheduler != null, "Scheduler should not be null")

    def onNext(elem: T): Ack = observer.onNext(elem)
    def onError(ex: Throwable): Unit = observer.onError(ex)
    def onComplete(): Unit = observer.onComplete()
  }
}