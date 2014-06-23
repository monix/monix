/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.reactive.observers

import monifu.reactive.Observer
import monifu.reactive.Ack.{Cancel, Continue}
import scala.concurrent.{Promise, Future}
import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.padded.Atomic
import monifu.reactive.Ack
import monifu.reactive.internals.FutureAckExtensions


/**
 * An observer wrapper that ensures the underlying implementation does not
 * receive concurrent onNext / onError / onComplete events - for those
 * cases in which the producer is emitting data too fast or concurrently
 * without fulfilling the back-pressure requirements.
 *
 * The `Future` returned by `onNext` on each call is guaranteed to be
 * completed only after downstream has processed the call.
 *
 * For high-contention scenarios it is very expensive in terms of both
 * memory usage and throughput. If one needs to send `onNext/onComplete`
 * events concurrently and buffering, but without the requirement for
 * `onNext` to return a `Future` that's only complete when the event was
 * processed by downstream (i.e. thread-safe buffering), then one is better
 * served by [[monifu.reactive.observers.BufferedObserver BufferedObserver]].
 */
final class ConcurrentObserver[-T] private (underlying: Observer[T])(implicit scheduler: Scheduler) extends Observer[T] {
  private[this] val ack = Atomic(Continue : Future[Ack])

  def onNext(elem: T) = {
    val p = Promise[Ack]()
    val newAck = p.future
    val oldAck = ack.getAndSet(newAck)

    oldAck.onCompleteNow {
      case Continue.IsSuccess =>
        underlying.onNext(elem).onCompleteNow(r => p.complete(r))
      case other =>
        p.complete(other)
    }

    newAck
  }

  def onError(ex: Throwable): Unit = {
    val oldAck = ack.getAndSet(Cancel)
    oldAck.onSuccess { case Continue => underlying.onError(ex) }
  }

  def onComplete(): Unit = {
    val oldAck = ack.getAndSet(Cancel)
    oldAck.onSuccess { case Continue => underlying.onComplete() }
  }
}

object ConcurrentObserver {
  def apply[T](observer: Observer[T])(implicit scheduler: Scheduler): ConcurrentObserver[T] =
    new ConcurrentObserver[T](observer)
}
