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

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Observer}

import scala.concurrent.{ExecutionContext, Future}
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
final class SafeObserver[-T] private (observer: Observer[T])
    (implicit ec: ExecutionContext)
  extends Observer[T] {

  private[this] var isDone = false

  def onNext(elem: T): Future[Ack] = {
    if (!isDone) {
      try {
        val result = observer.onNext(elem)
        if (result == Continue || result == Cancel || (result.isCompleted && result.value.get.isSuccess))
          result
        else
          result.recoverWith {
            case err =>
              onError(err)
              Cancel
          }
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
    if (!isDone) {
      isDone = true
      try observer.onError(ex) catch {
        case NonFatal(err) =>
          ec.reportFailure(err)
      }
    }
  }

  def onComplete() = {
    if (!isDone) {
      isDone = true
      try observer.onComplete() catch {
        case NonFatal(err) =>
          ec.reportFailure(err)
          Cancel
      }
    }
    else
      Cancel
  }
}

object SafeObserver {
  /**
   * Wraps an Observer instance into a SafeObserver.
   */
  def apply[T](observer: Observer[T])(implicit ec: ExecutionContext): SafeObserver[T] =
    observer match {
      case ref: SafeObserver[_] => ref.asInstanceOf[SafeObserver[T]]
      case _ => new SafeObserver[T](observer)
    }
}
