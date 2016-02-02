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

import monix.streams.Ack.{Cancel, Continue}
import monix.streams.{Ack, Subscriber}
import scala.concurrent.{Future, Promise}
import scala.util.Failure
import scala.util.control.NonFatal


/** A safe observer ensures too things:
  *
  * - errors triggered by downstream observers are caught and streamed to `onError`,
  *   while the upstream gets an `Ack.Cancel`, to stop sending events
  *
  * - once an `onError` or `onComplete` was emitted, the observer no longer accepts
  *   `onNext` events, ensuring that the Rx grammar is respected.
  *
  * - if downstream signals a `Cancel`, the observer no longer accepts any events,
  *   ensuring that the Rx grammar is respected.
  *
  * This implementation doesn't address multi-threading concerns in any way.
  */
final class SafeSubscriber[-T] private (subscriber: Subscriber[T])
  extends Subscriber[T] {

  implicit val scheduler = subscriber.scheduler

  @volatile
  private[this] var isDone = false

  def onNext(elem: T): Future[Ack] = {
    if (!isDone)
      try {
        val r = subscriber.onNext(elem)
        onCancelMarkDone(r)
      }
      catch {
        case NonFatal(ex) =>
          onError(ex)
          Cancel
      }
    else
      Cancel
  }

  def onError(ex: Throwable) = {
    if (!isDone) {
      isDone = true

      try subscriber.onError(ex) catch {
        case NonFatal(err) =>
          scheduler.reportFailure(err)
      }
    }
  }

  def onComplete() = {
    if (!isDone) {
      isDone = true

      try subscriber.onComplete() catch {
        case NonFatal(ex) =>
          try subscriber.onError(ex) catch {
            case NonFatal(err) =>
              scheduler.reportFailure(ex)
              scheduler.reportFailure(err)
          }
      }
    }
  }

  private[this] def onCancelMarkDone(source: Future[Ack]): Future[Ack] = {
    source match {
      case sync if sync.isCompleted =>
        sync.value.get match {
          case Continue.AsSuccess =>
            Continue

          case Cancel.AsSuccess =>
            isDone = true
            Cancel

          case Failure(ex) =>
            onError(sync.value.get.failed.get)
            Cancel

          case _ =>
            Continue
        }

      case async =>
        val p = Promise[Ack]()

        source.onComplete {
          case Continue.AsSuccess =>
            p.success(Continue)

          case Cancel.AsSuccess =>
            isDone = true
            p.success(Cancel)

          case Failure(ex) =>
            onError(ex)
            p.success(Cancel)

          case _ =>
            p.success(Continue)
        }

        p.future
    }
  }
}

object SafeSubscriber {
  /**
    * Wraps an Observer instance into a SafeObserver.
    */
  def apply[T](subscriber: Subscriber[T]): SafeSubscriber[T] =
    subscriber match {
      case ref: SafeSubscriber[_] => ref.asInstanceOf[SafeSubscriber[T]]
      case _ => new SafeSubscriber[T](subscriber)
    }
}
