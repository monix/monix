/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.reactive.observers

import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.Cancel
import monifu.reactive.internals._
import monifu.reactive.{Ack, Observer}

import scala.concurrent.Future
import scala.util.control.NonFatal


/**
 * A safe observer ensures too things:
 *
 * - errors triggered by downstream observers are caught and streamed to `onError`,
 *   while the upstream gets an `Ack.Cancel`, to stop sending events
 *
 * - once an `onError` or `onComplete` was emitted, the observer no longer accepts
 *   `onNext` events, ensuring that the Rx grammar is respected.
 *
 * This implementation doesn't address multi-threading concerns in any way.
 */
final class SafeObserver[-T] private (observer: Observer[T])(implicit s: Scheduler)
  extends Observer[T] {

  private[this] val isDone = Atomic(false)

  def onNext(elem: T): Future[Ack] = {
    if (!isDone()) {
      try {
        observer.onNext(elem)
          .onErrorCancelStream(observer, isDone)
      }
      catch {
        case NonFatal(ex) =>
          onError(ex)
          Cancel
      }
    }
    else
      Cancel
  }

  def onError(ex: Throwable) = {
    if (isDone.compareAndSet(expect = false, update = true))
      try observer.onError(ex) catch {
        case NonFatal(err) =>
          s.reportFailure(err)
      }
    else
      s.reportFailure(ex)
  }

  def onComplete() = {
    if (isDone.compareAndSet(expect = false, update = true))
      try observer.onComplete() catch {
        case NonFatal(err) =>
          s.reportFailure(err)
      }
  }
}

object SafeObserver {
  /**
   * Wraps an Observer instance into a SafeObserver.
   */
  def apply[T](observer: Observer[T])(implicit s: Scheduler): SafeObserver[T] =
    observer match {
      case ref: SafeObserver[_] => ref.asInstanceOf[SafeObserver[T]]
      case _ => new SafeObserver[T](observer)
    }
}
